package com.atomist.rug.cli.command.install;

import java.io.File;
import java.net.URI;

import org.apache.commons.cli.CommandLine;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;

import com.atomist.rug.cli.Constants;
import com.atomist.rug.cli.command.repo.AbstractRepositoryCommand;
import com.atomist.rug.cli.output.ProgressReportingOperationRunner;
import com.atomist.rug.cli.output.ProgressReportingTransferListener;
import com.atomist.rug.manifest.Manifest;
import com.atomist.source.ArtifactSource;

public class InstallCommand extends AbstractRepositoryCommand {

    @Override
    protected void doWithRepositorySession(RepositorySystem system, RepositorySystemSession session,
            ArtifactSource source, Manifest manifest, Artifact artifact, Artifact pom,
            Artifact metadata, CommandLine commandLine) {

        new ProgressReportingOperationRunner<InstallResult>(
                "Installing archive into local repository").run(indicator -> {

                    ((DefaultRepositorySystemSession) session).setTransferListener(
                            new ProgressReportingTransferListener(indicator, false));
                    ((DefaultRepositorySystemSession) session)
                            .setRepositoryListener(new AbstractRepositoryListener() {

                                @Override
                                public void artifactInstalled(RepositoryEvent event) {
                                    URI repo = session.getLocalRepository().getBasedir().toURI();
                                    URI artifact = event.getFile().toURI();

                                    indicator.report(String.format("  Installed %s %s %s",
                                            repo.relativize(artifact), Constants.DIVIDER,
                                            new File(repo).getAbsolutePath().toString()));
                                }
                            });

                    InstallRequest installRequest = new InstallRequest();
                    installRequest.addArtifact(artifact).addArtifact(pom).addArtifact(metadata);

                    return system.install(session, installRequest);
                });
        
        setResultView("install");
        addResultContext("artifact", artifact);
        addResultContext("source", source);
        addResultContext("manifest", manifest);
    }
}
