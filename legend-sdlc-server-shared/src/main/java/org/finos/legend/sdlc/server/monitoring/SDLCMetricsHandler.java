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

package org.finos.legend.sdlc.server.monitoring;

import io.prometheus.client.Counter;
import io.prometheus.client.SimpleTimer;
import io.prometheus.client.Summary;
import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.regex.Pattern;

public class SDLCMetricsHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SDLCMetricsHandler.class);

    private static final Counter OPERATION_START_COUNTER = createCounter("sdlc_operations", "Counter of SDLC operations started");
    private static final Summary OPERATION_COMPLETE_SUMMARY = createDurationSummary("sdlc_operations_completed", "Duration summary for SDLC operations completing with no error or redirect");
    private static final Summary OPERATION_REDIRECT_SUMMARY = createDurationSummary("sdlc_operations_redirected", "Duration summary for SDLC operations terminating with a redirect");
    private static final Summary OPERATION_ERROR_SUMMARY = createDurationSummary("sdlc_operations_errors", "Duration summary for SDLC operations terminating with an error");

    private static final String METRIC_PREFIX = "sdlc_";
    private static final Pattern METRIC_NAME_REPLACE = Pattern.compile("\\W++");
    private static final ConcurrentMutableMap<String, Summary> ADDITIONAL_SUMMARIES = ConcurrentHashMap.newMap();
    private static final ConcurrentMutableMap<String, Counter> ADDITIONAL_COUNTERS = ConcurrentHashMap.newMap();

    public static void operationStart()
    {
        OPERATION_START_COUNTER.inc();
    }

    public static void operationComplete(long startNanos, long endNanos, String durationMetricName)
    {
        operationTermination(startNanos, endNanos, OPERATION_COMPLETE_SUMMARY, durationMetricName);
    }

    public static void operationRedirect(long startNanos, long endNanos, String durationMetricName)
    {
        operationTermination(startNanos, endNanos, OPERATION_REDIRECT_SUMMARY, durationMetricName);
    }

    public static void operationError(long startNanos, long endNanos, String durationMetricName)
    {
        operationTermination(startNanos, endNanos, OPERATION_ERROR_SUMMARY, durationMetricName);
    }

    private static void operationTermination(long startNanos, long endNanos, Summary durationSummary, String durationMetricName)
    {
        double duration = SimpleTimer.elapsedSecondsFromNanos(startNanos, endNanos);
        durationSummary.observe(duration);
        if (durationMetricName != null)
        {
            Summary summary = getOrCreateAdditionalDurationSummary(durationMetricName);
            if (summary != null)
            {
                summary.observe(duration);
            }
        }
    }

    public static void incrementCounter(String name)
    {
        Counter counter = getOrCreateAdditionalCounter(name);
        if (counter != null)
        {
            counter.inc();
        }
    }

    private static Summary getOrCreateAdditionalDurationSummary(String name)
    {
        return getOrCreateAdditionalMetric(name, ADDITIONAL_SUMMARIES, SDLCMetricsHandler::createAdditionalDurationSummary);
    }

    private static Counter getOrCreateAdditionalCounter(String name)
    {
        return getOrCreateAdditionalMetric(name, ADDITIONAL_COUNTERS, SDLCMetricsHandler::createAdditionalCounter);
    }

    private static <T> T getOrCreateAdditionalMetric(String name, ConcurrentMutableMap<String, T> map, Function<String, T> creator)
    {
        T metric = map.get(name);
        if ((metric == null) && !map.containsKey(name))
        {
            synchronized (map)
            {
                metric = map.get(name);
                if ((metric == null) && !map.containsKey(name))
                {
                    metric = creator.apply(name);
                    map.put(name, metric);
                }
            }
        }
        return metric;
    }

    private static Summary createAdditionalDurationSummary(String name)
    {
        try
        {
            String metricName = generateMetricName(name);
            String metricHelp = name + " duration metric";
            return createDurationSummary(metricName, metricHelp);
        }
        catch (Exception e)
        {
            LOGGER.error("Error creating new summary \"{}\"", name, e);
            return null;
        }
    }

    private static Counter createAdditionalCounter(String name)
    {
        try
        {
            String metricName = generateMetricName(name);
            String metricHelp = name + " counter";
            return createCounter(metricName, metricHelp);
        }
        catch (Exception e)
        {
            LOGGER.error("Error creating new counter \"{}\"", name, e);
            return null;
        }
    }

    private static Summary createDurationSummary(String name, String help)
    {
        return Summary.build(name, help)
                .quantile(0.5, 0.05).quantile(0.9, 0.01).quantile(0.99, 0.001)
                .register();
    }

    private static Counter createCounter(String name, String help)
    {
        return Counter.build(name, help).register();
    }

    private static String generateMetricName(String name)
    {
        return METRIC_PREFIX + METRIC_NAME_REPLACE.matcher(name).replaceAll("_");
    }
}

