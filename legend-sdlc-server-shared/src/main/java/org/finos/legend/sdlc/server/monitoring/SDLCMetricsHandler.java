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

import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleTimer;
import io.prometheus.client.Summary;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;

import java.lang.reflect.Method;

public class SDLCMetricsHandler
{
    private static final String METRIC_PREFIX = "sdlc_";
    private static final MutableMap<String, Summary> sdlcMetrics = Maps.mutable.empty();
    private static final Gauge allSDLCOperations = Gauge.build().name("sdlc_operations").help("Operation endpoint calls gauge metric ").register();
    private static final Gauge allSDLCOperationErrors = Gauge.build().name("sdlc_operation_errors").help("Operation errors gauge metric ").register();

    public static void incrementSDLCOperationsGauge()
    {
        allSDLCOperations.inc();
    }

    public static void incrementSDLCOperationErrorsGauge()
    {
        allSDLCOperationErrors.inc();
    }

    public static void observe(String name, long startTimeInNano, long endTimeInNano)
    {
        Summary summary;
        synchronized (sdlcMetrics)
        {
            summary = sdlcMetrics.get(name);
            if (summary == null)
            {
                summary = Summary.build().name(generateMetricName(name))
                        .quantile(0.5, 0.05).quantile(0.9, 0.01).quantile(0.99, 0.001)
                        .help(name + " duration metrics")
                        .register();
                sdlcMetrics.put(name, summary);
            }
        }
        summary.observe(SimpleTimer.elapsedSecondsFromNanos(startTimeInNano, endTimeInNano));
    }

    public static String generateMetricName(String name)
    {
        return METRIC_PREFIX + name
                .replace("/", "_")
                .replace("-", "_")
                .replace("{", "")
                .replace("}", "")
                .replaceAll(" ", "_");
    }
}

