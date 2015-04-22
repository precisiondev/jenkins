package hudson.maven;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Assert;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.ExtractResourceWithChangesSCM;
import org.jvnet.hudson.test.ExtractChangeLogSet;

import hudson.Launcher;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.maven.reporters.MavenFingerprinter;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Maven.MavenInstallation;

import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Andrew Bayer
 */
public class MavenMultiModuleTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * NPE in {@code build.getProject().getWorkspace()} for {@link MavenBuild}.
     */
    @Bug(4192)
    @Test public void multiModMavenWsExists() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
	    assertFalse("MavenModuleSet.isNonRecursive() should be false", m.isNonRecursive());
        j.buildAndAssertSuccess(m);
    }

    @Test public void incrementalMultiModMaven() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.getReporters().add(new TestReporter());
        m.getReporters().add(new MavenFingerprinter());
    	m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod.zip"),
    						   getClass().getResource("maven-multimod-changes.zip")));
    
    	j.buildAndAssertSuccess(m);
    
    	// Now run a second build with the changes.
    	m.setIncrementalBuild(true);
        j.buildAndAssertSuccess(m);
    
    	MavenModuleSetBuild pBuild = m.getLastBuild();
    	ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();
    
    	assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());

    	for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
    	    String parentModuleName = modBuild.getParent().getModuleName().toString();
    	    if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleA")) {
    	        assertEquals("moduleA should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
    	    }
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleB")) {
    	        assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleC")) {
    	        assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	}	
	
	    long summedModuleDuration = 0;
	    for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
	        summedModuleDuration += modBuild.getDuration();
	    }
	    assertTrue("duration of moduleset build should be greater-equal than sum of the module builds",
	            pBuild.getDuration() >= summedModuleDuration);
	    
	    assertFingerprintWereRecorded(pBuild);
    }

    private void assertFingerprintWereRecorded(MavenModuleSetBuild modulesetBuild) {
        boolean mustHaveFingerprints = false;
        for (MavenBuild moduleBuild : modulesetBuild.getModuleLastBuilds().values()) {
            if (moduleBuild.getResult() != Result.NOT_BUILT && moduleBuild.getResult() != Result.ABORTED) {
                assertFingerprintWereRecorded(moduleBuild);
                mustHaveFingerprints = true;
            }
        }
        
        if (mustHaveFingerprints) {
            FingerprintAction action = modulesetBuild.getAction(FingerprintAction.class);
            Assert.assertNotNull(action);
            Assert.assertFalse(action.getFingerprints().isEmpty());
        }
    }

    private void assertFingerprintWereRecorded(MavenBuild moduleBuild) {
        FingerprintAction action = moduleBuild.getAction(FingerprintAction.class);
        Assert.assertNotNull(action);
        Assert.assertFalse(action.getFingerprints().isEmpty());
        
        MavenArtifactRecord artifactRecord = moduleBuild.getAction(MavenArtifactRecord.class);
        Assert.assertNotNull(artifactRecord);
        String fingerprintName = artifactRecord.mainArtifact.groupId + ":" + artifactRecord.mainArtifact.fileName;
        
        Assert.assertTrue("Expected fingerprint " + fingerprintName + " in module build " + moduleBuild,
              action.getFingerprints().containsKey(fingerprintName));
        
        // we should assert more - i.e. that all dependencies are fingerprinted, too,
        // but it's complicated to find out the dependencies of the build
    }

    @Bug(5357)
    @Test public void incrRelMultiModMaven() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.setRootPOM("parent/pom.xml");
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod-rel-base.zip"),
						   getClass().getResource("maven-multimod-changes.zip")));
        
        j.buildAndAssertSuccess(m);
        
        // Now run a second build with the changes.
        m.setIncrementalBuild(true);
        j.buildAndAssertSuccess(m);
        
        MavenModuleSetBuild pBuild = m.getLastBuild();
        ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();
        
        assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());

        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleA")) {
                assertEquals("moduleA should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }	
	
        long summedModuleDuration = 0;
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            summedModuleDuration += modBuild.getDuration();
        }
        assertTrue("duration of moduleset build should be greater-equal than sum of the module builds",
        pBuild.getDuration() >= summedModuleDuration);
    }

        
    @Bug(6544)
    @Ignore("kutzi 10/10/11 ignore test until I can figure out why it fails sometimes")
    @Test public void estimatedDurationForIncrementalMultiModMaven()
            throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource(
                "maven-multimod.zip"), getClass().getResource(
                "maven-multimod-changes.zip")));

        j.buildAndAssertSuccess(m);

        // Now run a second, incremental build with the changes.
        m.setIncrementalBuild(true);
        j.buildAndAssertSuccess(m);

        MavenModuleSetBuild lastBuild = m.getLastBuild();
        MavenModuleSetBuild previousBuild = lastBuild.getPreviousBuild();
        assertNull("There should be only one previous build", previousBuild.getPreviousBuild());
        
        // Since the estimated duration is calculated based on the previous builds
        // and there was only one previous build (which built all modules) and this build
        // did only build one module, the estimated duration of this build must be
        // smaller than the duration of the previous build.
        // (It's highly unlikely that the durations are equal, but I've already seen it fail.
        // Therefore <= instead of <)
        assertTrue("Estimated duration should be <= " + previousBuild.getDuration()
                + ", but is " + lastBuild.getEstimatedDuration(),
                lastBuild.getEstimatedDuration() <= previousBuild.getDuration());
    }
    
    /**
     * NPE in {@code getChangeSetFor(m)} in {@link MavenModuleSetBuild} when incremental build is
     * enabled and a new module is added.
     */
    @Test public void newModMultiModMaven() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod.zip"),
                getClass().getResource("maven-multimod-changes.zip")));

        m.setIncrementalBuild(true);
        j.buildAndAssertSuccess(m);
    }

    /**
     * When "-N' or "--non-recursive" show up in the goals, any child modules should be ignored.
     */
    @Bug(4491)
    @Test public void multiModMavenNonRecursiveParsing() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.setGoals("clean install -N");
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));

        j.buildAndAssertSuccess(m);

        MavenModuleSetBuild pBuild = m.getLastBuild();

        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:multimod-top")) {
                assertEquals("moduleA should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleA")) {
                assertEquals("moduleA should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleB")) {
                assertEquals("moduleB should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleC")) {
                assertEquals("moduleC should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
	    
        }	
	
    }

    /**
     * Module failures in build X should lead to those modules being re-run in build X+1, even if
     * incremental build is enabled and nothing changed in those modules.
     */
    @Bug(4152)
    @Test public void incrementalMultiModWithErrorsMaven() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.setIncrementalBuild(true);
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod-incr.zip"),
						   getClass().getResource("maven-multimod-changes.zip")));

        j.assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
        MavenModuleSetBuild pBuild = m.getLastBuild();
        
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }   

        // Now run a second build with the changes.
        j.assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());

    	pBuild = m.getLastBuild();
    	ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();

    	assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());
    	// changelog contains a change for module B
    	assertEquals("Parent build should have Result.UNSTABLE", Result.UNSTABLE, pBuild.getResult());
	
    	for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
    	    String parentModuleName = modBuild.getParent().getModuleName().toString();
    	    // A must be build again, because it was UNSTABLE before
    	    if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
    	        assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
    	    }
    	    // B must be build, because it has changes
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
    	        assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	    // C must be build, because it depends on B
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
    	        assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	    // D must not be build
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
    	        assertEquals("moduleD should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
    	    }
    	}
    }
    
    /**
     * If "deploy modules" is checked and aggregator build failed
     * then all modules build this time, have to be build next time, again.
     */
    @Bug(5121)
    @Test public void incrementalRedeployAfterAggregatorError() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.setIncrementalBuild(true);
        m.getReporters().add(new TestReporter());
        m.getPublishers().add(new DummyRedeployPublisher());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod-incr.zip"),
                           getClass().getResource("maven-multimod-changes.zip")));

        j.assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
        MavenModuleSetBuild pBuild = m.getLastBuild();
        
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }   

        // Now run a second build.
        j.assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());

        pBuild = m.getLastBuild();
        ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();

        assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());
        // changelog contains a change for module B
        assertEquals("Parent build should have Result.UNSTABLE", Result.UNSTABLE, pBuild.getResult());
    
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            // A must be build again, because it was UNSTABLE before
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            // B must be build, because it has changes
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            // C must be build, because it depends on B
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            // D must be build again, because it needs to be deployed now
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }
    }
    
    /**
     * Test failures in a child module should lead to the parent being marked as unstable.
     */
    @Bug(4378)
    @Test public void multiModWithTestFailuresMaven() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod-incr.zip")));

        j.assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());

        MavenModuleSetBuild pBuild = m.getLastBuild();

        assertEquals("Parent build should have Result.UNSTABLE", Result.UNSTABLE, pBuild.getResult());
	
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }	
    }
    
    @Bug(8484)
    @Test public void multiModMavenNonRecursive() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = j.createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        m.setGoals( "-N validate" );
        assertTrue("MavenModuleSet.isNonRecursive() should be true", m.isNonRecursive());
        j.buildAndAssertSuccess(m);
        assertEquals("not only one module", 1, m.getModules().size());
    }    

    @Bug(17713)
    @Test public void modulesPageLinks() throws Exception {
        j.configureMaven3();
        MavenModuleSet ms = j.createMavenProject();
        ms.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        j.buildAndAssertSuccess(ms);
        MavenModule m = ms.getModule("org.jvnet.hudson.main.test.multimod:moduleA");
        assertNotNull(m);
        assertEquals(1, m.getLastBuild().getNumber());
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage modulesPage = wc.getPage(ms, "modules");
        modulesPage.getAnchorByText(m.getDisplayName()).openLinkInNewWindow();
    }

    /*
    @Test public void parallelMultiModMavenWsExists() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
	m.setAggregatorStyleBuild(false);
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

	for (MavenModule mod : m.sortedActiveModules) {
	    while (mod.getLastBuild() == null) {
		Thread.sleep(500);
	    }

	    while (mod.getLastBuild().isBuilding()) {
		Thread.sleep(500);
	    }

	    assertBuildStatusSuccess(mod.getLastBuild());
	}
	

	
    }
    
    @Test public void privateRepoParallelMultiModMavenWsExists() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
	m.setAggregatorStyleBuild(false);
	m.setUsePrivateRepository(true);
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

	for (MavenModule mod : m.sortedActiveModules) {
	    while (mod.getLastBuild() == null) {
		Thread.sleep(500);
	    }
	    
	    while (mod.getLastBuild().isBuilding()) {
		Thread.sleep(500);
	    }

	    assertBuildStatusSuccess(mod.getLastBuild());
	}

    }
    */
    private static class TestReporter extends MavenReporter {
        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            assertNotNull(build.getProject().getWorkspace());
            assertNotNull(build.getWorkspace());
            return true;
        }
    }
    
    private static class DummyRedeployPublisher extends RedeployPublisher {
        public DummyRedeployPublisher() {
            super("", "", false, false);
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException,
                IOException {
            return true;
        }
    }
}
