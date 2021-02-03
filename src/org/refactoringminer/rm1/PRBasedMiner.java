package org.refactoringminer.rm1;

import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.diff.UMLModelDiff;
import org.kohsuke.github.*;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringMinerTimedOutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

public class PRBasedMiner implements PRMiner {
    private static final Logger logger = LoggerFactory.getLogger(GitHistoryRefactoringMinerImpl.class);
    private final GitHistoryRefactoringMinerImpl refactoringMiner;

    public PRBasedMiner(GitHistoryRefactoringMinerImpl refactoringMiner) {
        this.refactoringMiner = refactoringMiner;
    }

    public void detectAtPullRequest(String cloneURL, int pullRequestId, RefactoringHandler handler, int timeout) throws IOException {
        GitHub gitHub = refactoringMiner.connectToGitHub();
        String repoName = refactoringMiner.extractRepositoryName(cloneURL);
        GHRepository repository = gitHub.getRepository(repoName);
        GHPullRequest pullRequest = repository.getPullRequest(pullRequestId);
        String currentCommitId = pullRequest.listCommits().toList().get(0).getSha();
        List<Refactoring> refactoringsAtRevision = Collections.emptyList();
        try {
            Set<String> repositoryDirectoriesBefore = ConcurrentHashMap.newKeySet();
            Set<String> repositoryDirectoriesCurrent = ConcurrentHashMap.newKeySet();
            Map<String, String> fileContentsBefore = new ConcurrentHashMap<String, String>();
            Map<String, String> fileContentsCurrent = new ConcurrentHashMap<String, String>();
            Map<String, String> renamedFilesHint = new ConcurrentHashMap<String, String>();
            populateWithGitHubAPI(repository, pullRequestId, fileContentsBefore, fileContentsCurrent, renamedFilesHint, repositoryDirectoriesBefore, repositoryDirectoriesCurrent);
            UMLModel currentUMLModel = refactoringMiner.createModel(fileContentsCurrent, repositoryDirectoriesCurrent);
            UMLModel parentUMLModel = refactoringMiner.createModel(fileContentsBefore, repositoryDirectoriesBefore);
            //  Diff between currentModel e parentModel
            UMLModelDiff modelDiff = parentUMLModel.diff(currentUMLModel, renamedFilesHint);
            refactoringsAtRevision = modelDiff.getRefactorings();
            refactoringsAtRevision = refactoringMiner.filter(refactoringsAtRevision);
        } catch (RefactoringMinerTimedOutException e) {
            logger.warn(String.format("Ignored revision %s due to timeout", currentCommitId), e);
            handler.handleException(currentCommitId, e);
        } catch (Exception e) {
            logger.warn(String.format("Ignored revision %s due to error", currentCommitId), e);
            handler.handleException(currentCommitId, e);
        }
        handler.handle(currentCommitId, refactoringsAtRevision);
    }

    public void populateWithGitHubAPI(GHRepository repository, Integer pullRequestId,
                                      Map<String, String> filesBefore, Map<String, String> filesCurrent, Map<String, String> renamedFilesHint,
                                      Set<String> repositoryDirectoriesBefore, Set<String> repositoryDirectoriesCurrent) throws IOException{
        GHPullRequest pullRequest = repository.getPullRequest(pullRequestId);
        final String currentCommit = pullRequest.getHead().getSha();
        final String prevCommit = pullRequest.getBase().getSha();

        StreamSupport.stream(pullRequest.listFiles().spliterator(), false).filter(p -> p.getFilename().endsWith(".java")).forEach(entry -> {
            final String filename = entry.getFilename();
            String previousFilename = entry.getPreviousFilename() != null ? entry.getPreviousFilename() : filename;
            logger.info("fetching: " + entry.getStatus() + ": " + previousFilename + "->" + filename);
            try {
                if (previousFilename != null && !entry.getStatus().equals("added")) {
                    GHContent before = repository.getFileContent(previousFilename, prevCommit);
                    filesBefore.put(previousFilename, before.getContent());
                }
            } catch (Exception ex) {
                logger.warn("Failed to fetch " + previousFilename + " commit " + prevCommit + " due to " + ex.getMessage(), ex);
            }
            if (filename != null && !"removed".equals(entry.getStatus())) {
                try {
                    GHContent after = repository.getFileContent(filename, currentCommit);
                    filesCurrent.put(filename, after.getContent());
                } catch (Exception ex) {
                    logger.warn("Failed to fetch " + filename + " commit " + currentCommit + " due to " + ex.getMessage(), ex);
                }
            }

        });

    }

}
