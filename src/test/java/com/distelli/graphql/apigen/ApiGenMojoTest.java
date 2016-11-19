package com.distelli.graphql.apigen;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.TestMavenRuntime;
import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

public class ApiGenMojoTest {
    @Rule
    public TestResources resources = new TestResources();

    @Rule
    public TestMavenRuntime maven = new TestMavenRuntime();

    @Test
    public void test() throws Exception {
        File basedir = resources.getBasedir("basic");
        maven.executeMojo(basedir, "apigen");
    }
}
