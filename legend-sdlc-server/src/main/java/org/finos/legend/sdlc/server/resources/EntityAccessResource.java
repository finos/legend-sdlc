// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.server.resources;

import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class EntityAccessResource extends BaseResource
{
    private static final char TAGGED_VALUE_DELIMITER = '/';

    protected List<String> getEntityPaths(EntityAccessContext entityAccessContext, Set<String> classifierPaths, Set<String> packages, boolean includeSubPackages, String nameRegex, Set<String> stereotypes, Collection<String> taggedValueRegexes)
    {
        Predicate<String> entityPathPredicate = getEntityPathPredicate(packages, includeSubPackages, nameRegex);
        Predicate<String> classifierPathPredicate = getClassifierPathPredicate(classifierPaths);
        Predicate<Map<String, ?>> contentPredicate = getContentPredicate(stereotypes, taggedValueRegexes);
        return entityAccessContext.getEntityPaths(entityPathPredicate, classifierPathPredicate, contentPredicate);
    }

    protected List<Entity> getEntities(EntityAccessContext entityAccessContext, Set<String> classifierPaths, Set<String> packages, boolean includeSubPackages, String nameRegex, Set<String> stereotypes, Collection<String> taggedValueRegexes, boolean excludeInvalidEntities)
    {
        Predicate<String> entityPathPredicate = getEntityPathPredicate(packages, includeSubPackages, nameRegex);
        Predicate<String> classifierPathPredicate = getClassifierPathPredicate(classifierPaths);
        Predicate<Map<String, ?>> contentPredicate = getContentPredicate(stereotypes, taggedValueRegexes);
        return entityAccessContext.getEntities(entityPathPredicate, classifierPathPredicate, contentPredicate, excludeInvalidEntities);
    }

    private Predicate<String> getEntityPathPredicate(Set<String> packages, boolean includeSubPackages, String nameRegex)
    {
        Pattern namePattern = compileRegex(nameRegex);
        if (packages == null)
        {
            return (namePattern == null) ? null : path -> pathMatchesName(path, namePattern);
        }

        switch (packages.size())
        {
            case 0:
            {
                return (namePattern == null) ? null : path -> pathMatchesName(path, namePattern);
            }
            case 1:
            {
                String pkg = packages.stream().findAny().get();
                if (namePattern == null)
                {
                    return includeSubPackages ? path -> pathMatchesPackageOrSubPackage(path, pkg) : path -> pathMatchesPackage(path, pkg);
                }
                return includeSubPackages ? path -> pathMatchesPackageOrSubPackageAndName(path, pkg, namePattern) : path -> pathMatchesPackageAndName(path, pkg, namePattern);
            }
            default:
            {
                if (namePattern == null)
                {
                    return includeSubPackages ? path -> pathMatchesAnyPackageOrSubPackage(path, packages) : path -> pathMatchesAnyPackage(path, packages);
                }
                return includeSubPackages ? path -> pathMatchesAnyPackageOrSubpackageAndName(path, packages, namePattern) : path -> pathMatchesAnyPackageAndName(path, packages, namePattern);
            }
        }
    }

    private Predicate<String> getClassifierPathPredicate(Set<String> classifierPaths)
    {
        if (classifierPaths == null)
        {
            return null;
        }
        switch (classifierPaths.size())
        {
            case 0:
            {
                return null;
            }
            case 1:
            {
                String classifierPath = classifierPaths.stream().findAny().get();
                return classifierPath::equals;
            }
            default:
            {
                return classifierPaths::contains;
            }
        }
    }

    private Predicate<Map<String, ?>> getContentPredicate(Set<String> stereotypes, Collection<String> taggedValueRegexes)
    {
        Predicate<Map<String, ?>> predicate = null;
        if ((stereotypes != null) && !stereotypes.isEmpty())
        {
            predicate = content -> entityHasAnyStereotype(content, stereotypes);
        }
        if ((taggedValueRegexes != null) && !taggedValueRegexes.isEmpty())
        {
            Map<String, Pattern> taggedValuePatterns = Maps.mutable.ofInitialCapacity(taggedValueRegexes.size());
            for (String taggedValueRegex : taggedValueRegexes)
            {
                int delimIndex = taggedValueRegex.indexOf(TAGGED_VALUE_DELIMITER);
                String tag;
                String regex;
                if (delimIndex < 0)
                {
                    tag = taggedValueRegex;
                    regex = "";
                }
                else
                {
                    tag = taggedValueRegex.substring(0, delimIndex).trim();
                    regex = (delimIndex == taggedValueRegex.length() - 1) ? "" : taggedValueRegex.substring(delimIndex + 1).trim();
                }
                Pattern pattern = compileRegex(regex);
                if (pattern != null)
                {
                    Pattern old = taggedValuePatterns.put(tag, pattern);
                    if (old != null)
                    {
                        String oldRegex = old.pattern();
                        if (!oldRegex.equals(regex))
                        {
                            // combine patterns disjunctively
                            String newRegex = (((old.flags() & Pattern.LITERAL) == Pattern.LITERAL) ? Pattern.quote(oldRegex) : oldRegex) + "|" + (((pattern.flags() & Pattern.LITERAL) == Pattern.LITERAL) ? Pattern.quote(regex) : regex);
                            Pattern newPattern = Pattern.compile(newRegex, Pattern.CASE_INSENSITIVE);
                            taggedValuePatterns.put(tag, newPattern);
                        }
                    }
                }
            }

            Predicate<Map<String, ?>> taggedValuePred = content -> entityMatchesAnyTaggedValuePattern(content, taggedValuePatterns);
            predicate = (predicate == null) ? taggedValuePred : predicate.and(taggedValuePred);
        }
        return predicate;
    }

    private static Pattern compileRegex(String regex)
    {
        if (regex == null)
        {
            return null;
        }

        try
        {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }
        catch (Exception ignore)
        {
            // Not a valid regex, so interpret as a literal string
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
        }
    }

    private static boolean pathMatchesName(String path, Pattern namePattern)
    {
        return pathMatchesName(path, namePattern, path.lastIndexOf(':'));
    }

    private static boolean pathMatchesPackage(String path, String pkg)
    {
        return pathMatchesPackage(path, pkg, path.lastIndexOf(':'));
    }

    private static boolean pathMatchesPackageOrSubPackage(String path, String pkg)
    {
        return pathMatchesPackageOrSubPackage(path, pkg, path.lastIndexOf(':'));
    }

    private static boolean pathMatchesAnyPackage(String path, Set<String> packages)
    {
        return pathMatchesAnyPackage(path, packages, path.lastIndexOf(':'));
    }

    private static boolean pathMatchesAnyPackageOrSubPackage(String path, Set<String> packagesWithTrailingColons)
    {
        return pathMatchesAnyPackageOrSubPackage(path, packagesWithTrailingColons, path.lastIndexOf(':'));
    }

    private static boolean pathMatchesPackageAndName(String path, String pkg, Pattern namePattern)
    {
        int lastColonIndex = path.lastIndexOf(':');
        return pathMatchesPackage(path, pkg, lastColonIndex) && pathMatchesName(path, namePattern, lastColonIndex);
    }

    private static boolean pathMatchesPackageOrSubPackageAndName(String path, String pkg, Pattern namePattern)
    {
        int lastColonIndex = path.lastIndexOf(':');
        return pathMatchesPackageOrSubPackage(path, pkg, lastColonIndex) && pathMatchesName(path, namePattern, lastColonIndex);
    }

    private static boolean pathMatchesAnyPackageAndName(String path, Set<String> packages, Pattern namePattern)
    {
        int lastColonIndex = path.lastIndexOf(':');
        return pathMatchesName(path, namePattern, lastColonIndex) && pathMatchesAnyPackage(path, packages, lastColonIndex);
    }

    private static boolean pathMatchesAnyPackageOrSubpackageAndName(String path, Set<String> packages, Pattern namePattern)
    {
        int lastColonIndex = path.lastIndexOf(':');
        return pathMatchesName(path, namePattern, lastColonIndex) && pathMatchesAnyPackageOrSubPackage(path, packages, lastColonIndex);
    }

    private static boolean pathMatchesName(String path, Pattern namePattern, int lastColonIndex)
    {
        return namePattern.matcher(path).region(lastColonIndex + 1, path.length()).find();
    }

    private static boolean pathMatchesPackage(String path, String pkg, int lastColonIndex)
    {
        return (pkg.length() == (lastColonIndex - 1)) && path.startsWith(pkg);
    }

    private static boolean pathMatchesPackageOrSubPackage(String path, String pkg, int lastColonIndex)
    {
        return (pkg.length() <= (lastColonIndex - 1)) && (path.charAt(pkg.length()) == ':') && path.startsWith(pkg);
    }

    private static boolean pathMatchesAnyPackage(String path, Set<String> packages, int lastColonIndex)
    {
        return packages.contains(path.substring(0, lastColonIndex - 1));
    }

    private static boolean pathMatchesAnyPackageOrSubPackage(String path, Set<String> packages, int lastColonIndex)
    {
        String pathPackage = path.substring(0, lastColonIndex - 1);
        return packages.contains(pathPackage) || packages.stream().anyMatch(pkg -> (pathPackage.length() > pkg.length()) && pathPackage.startsWith(pkg) && (pathPackage.charAt(pkg.length()) == ':'));
    }

    private static boolean entityHasAnyStereotype(Map<String, ?> entityContent, Set<String> stereotypes)
    {
        Object entityStereotypes = entityContent.get("stereotypes");
        if (entityStereotypes instanceof Iterable)
        {
            for (Object entityStereotype : (Iterable<?>) entityStereotypes)
            {
                if (entityStereotype instanceof Map)
                {
                    String stereotypePath = getAnnotationPath((Map<?, ?>) entityStereotype);
                    if ((stereotypePath != null) && stereotypes.contains(stereotypePath))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean entityMatchesAnyTaggedValuePattern(Map<String, ?> entityContent, Map<String, ? extends Pattern> taggedValuePatterns)
    {
        Object entityTaggedValues = entityContent.get("taggedValues");
        if (entityTaggedValues instanceof Iterable)
        {
            for (Object entityTaggedValue : (Iterable<?>) entityTaggedValues)
            {
                if (entityTaggedValue instanceof Map)
                {
                    Object entityTag = ((Map<?, ?>) entityTaggedValue).get("tag");
                    if (entityTag instanceof Map)
                    {
                        String tagPath = getAnnotationPath((Map<?, ?>) entityTag);
                        if (tagPath != null)
                        {
                            Pattern pattern = taggedValuePatterns.get(tagPath);
                            if (pattern != null)
                            {
                                Object value = ((Map<?, ?>) entityTaggedValue).get("value");
                                if ((value instanceof String) && pattern.matcher((String) value).find())
                                {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static String getAnnotationPath(Map<?, ?> annotationJson)
    {
        Object profile = annotationJson.get("profile");
        if (profile instanceof String)
        {
            Object value = annotationJson.get("value");
            if (value instanceof String)
            {
                return profile + "." + value;
            }
        }
        return null;
    }
}
