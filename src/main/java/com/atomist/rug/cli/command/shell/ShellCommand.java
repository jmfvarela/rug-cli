package com.atomist.rug.cli.command.shell;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.atomist.project.archive.ResolvedDependency;
import com.atomist.project.archive.RugResolver;
import com.atomist.rug.cli.Constants;
import com.atomist.rug.cli.command.AbstractAnnotationBasedCommand;
import com.atomist.rug.cli.command.CommandContext;
import com.atomist.rug.cli.command.CommandEventListener;
import com.atomist.rug.cli.command.CommandEventListenerAdapter;
import com.atomist.rug.cli.command.CommandEventListenerRegistry;
import com.atomist.rug.cli.command.annotation.Command;
import com.atomist.rug.cli.command.fs.ArtifactSourceFileWatcherFactory;
import com.atomist.rug.cli.command.fs.ArtifactSourceFileWatcherFactory.FileWatcher;
import com.atomist.rug.cli.output.ProgressReportingOperationRunner;
import com.atomist.rug.cli.output.Style;
import com.atomist.rug.cli.settings.Settings;
import com.atomist.rug.cli.utils.ArtifactDescriptorUtils;
import com.atomist.rug.cli.version.VersionUtils;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.LocalArtifactDescriptor;
import com.atomist.rug.resolver.metadata.MetadataWriter;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;

public class ShellCommand extends AbstractAnnotationBasedCommand {

    private static final String BANNER_CONFIG_KEY = "shell-banner-enable";

    private static final String banner = "" + "  ____                 ____ _     ___ \n"
            + " |  _ \\ _   _  __ _   / ___| |   |_ _|\n"
            + " | |_) | | | |/ _` | | |   | |    | | \n"
            + " |  _ <| |_| | (_| | | |___| |___ | | \n"
            + " |_| \\_\\\\__,_|\\__, |  \\____|_____|___|\n" + " %s      |___/ %s";

    @Command
    public void run(ArtifactSource source, ArtifactDescriptor artifact,
            ResolvedDependency operations, RugResolver resolver, Settings settings) {

        new ProgressReportingOperationRunner<Void>(String.format("Initializing shell for %s",
                ArtifactDescriptorUtils.coordinates(artifact))).run((reporter) -> {
                    registerFileSystemWatcherEventListener(artifact, operations);
                    registerOperationsEventListener(source, artifact, operations, resolver);
                    return null;
                });

        if (!Constants.isShell()) {
            printBanner(settings);
        }
        Constants.setShell(true);
    }

    private void registerFileSystemWatcherEventListener(ArtifactDescriptor artifact,
            ResolvedDependency operations) {
        if (artifact instanceof LocalArtifactDescriptor && operations != null) {
            CommandContext.save(FileWatcher.class,
                    ArtifactSourceFileWatcherFactory.create(artifact));
        }
    }

    private void registerOperationsEventListener(ArtifactSource source, ArtifactDescriptor artifact,
            ResolvedDependency operations, RugResolver resolver) {

        CommandEventListener listener = new OperationsLoadedEventListener(source);
        listener.operationsLoaded(artifact, operations, resolver);
        CommandEventListenerRegistry.register(listener);
    }

    private void printBanner(Settings settings) {
        if (settings.getConfigValue(BANNER_CONFIG_KEY, true)) {
            String version = VersionUtils.readVersion().orElse("0.0.0");
            log.newline();
            log.info(banner, Style.gray("Atomist"), Style.gray(StringUtils.leftPad(version, 18)));
        }
        log.newline();
        log.info(
                "Press 'Tab' to complete. Type 'help' and hit 'Return' for help, and 'exit' to quit.");
    }

    private static class OperationsLoadedEventListener extends CommandEventListenerAdapter {

        private ArtifactSource source;

        public OperationsLoadedEventListener(ArtifactSource source) {
            this.source = source;
        }

        @Override
        public void operationsLoaded(ArtifactDescriptor artifact, ResolvedDependency operations,
                RugResolver resolver) {
            if (artifact != null && operations != null) {
                FileArtifact file = MetadataWriter.createWithoutExcludes(operations.rugs(),
                        artifact, source, null, Constants.cliClient());

                try {
                    FileUtils.write(ShellUtils.SHELL_OPERATIONS, file.content(),
                            StandardCharsets.ISO_8859_1);
                    FileUtils.forceDeleteOnExit(ShellUtils.SHELL_OPERATIONS);
                }
                catch (IOException e) {
                    // We can't write the operations out to a file, so what?
                }
            }
        }
    }
}
