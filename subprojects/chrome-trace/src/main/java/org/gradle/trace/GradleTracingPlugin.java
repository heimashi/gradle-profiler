package org.gradle.trace;

import static org.gradle.trace.util.ReflectionUtil.invokerGetter;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.trace.listener.BuildOperationListenerAdapter;
import org.gradle.trace.monitoring.GCMonitoring;
import org.gradle.trace.monitoring.SystemMonitoring;
import org.gradle.trace.util.TimeUtil;

import java.io.File;
import java.util.HashMap;

import javax.inject.Inject;

public class GradleTracingPlugin implements Plugin<Gradle> {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String PHASE_BUILD = "build duration";

    private final BuildRequestMetaData buildRequestMetaData;
    private TraceResult traceResult;
    private final SystemMonitoring systemMonitoring = new SystemMonitoring();
    private final GCMonitoring gcMonitoring = new GCMonitoring();
    private BuildOperationListenerAdapter buildOperationListener;

    private void init(GradleInternal gradle, File traceFile) {
        //this.buildRequestMetaData = gradle.getServices().get(BuildRequestMetaData.class);
        traceResult = new TraceResult(traceFile);
        systemMonitoring.start(traceResult);
        gcMonitoring.start(traceResult);
        buildOperationListener = BuildOperationListenerAdapter.create(gradle, traceResult);
        gradle.addListener(new TraceFinalizerAdapter(gradle));
    }

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
    }

    @Override
    public void apply(Gradle gradle) {
        gradle.rootProject(new Action<Project>() {
            @Override
            public void execute(Project project) {
                File file = (File) project.getProperties().getOrDefault("chromeTraceFile", null);
                if (file == null) {
                    file = new File(project.getBuildDir().getAbsolutePath() + File.separator + "trace.html");
                }
                file.getParentFile().mkdirs();
                start((GradleInternal) gradle, file);
            }
        });
    }

    @SuppressWarnings("unused")
    public void start(GradleInternal gradle, File traceFile) {
        init(gradle, traceFile);
    }

    private class TraceFinalizerAdapter extends BuildAdapter {
        private final Gradle gradle;

        private TraceFinalizerAdapter(Gradle gradle) {
            this.gradle = gradle;
        }

        @Override
        public void buildFinished(BuildResult result) {
            systemMonitoring.stop();
            gcMonitoring.stop();
            buildOperationListener.remove();

            traceResult.start(PHASE_BUILD, CATEGORY_PHASE, TimeUtil.toNanoTime(getStartTime()));
            traceResult.finish(PHASE_BUILD, System.nanoTime(), new HashMap<>());
            traceResult.finalizeTraceFile();

            gradle.removeListener(this);
        }

        private long getStartTime() {
            try {
                return buildRequestMetaData.getStartTime();
            } catch (NoSuchMethodError e) {
                return (long) invokerGetter(invokerGetter(buildRequestMetaData, "getBuildTimeClock"), "getStartTime");
            }
        }
    }
}
