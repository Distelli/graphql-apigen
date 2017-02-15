package com.distelli.graphql.apigen;

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.ResolutionScope;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import org.apache.maven.model.Resource;
import java.util.Collections;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.util.ArrayList;
import java.util.Arrays;

@Mojo(name="apigen",
      defaultPhase=LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution=ResolutionScope.COMPILE)
@Execute(goal="apigen")
public class ApiGenMojo extends AbstractMojo {
    @Parameter(defaultValue="${project}", readonly=true)
    private MavenProject project;

    @Parameter(name="sourceDirectory",
               defaultValue="schema")
    private File sourceDirectory;

    @Parameter(name="outputDirectory",
               defaultValue="target/generated-sources/apigen")
    private File outputDirectory;

    @Parameter(name="guiceModuleName")
    private String guiceModuleName;

    @Parameter(name="defaultPackageName", defaultValue = "com.graphql.generated")
    private String defaultPackageName;

    private File makeAbsolute(File in) {
        if ( in.isAbsolute() ) return in;
        return new File(project.getBasedir(), in.toString());
    }

    private ClassLoader getCompileClassLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        int idx = 0;
        String ignored = project.getBuild().getOutputDirectory();
        getLog().debug("ignore="+ignored);
        for ( String path : project.getCompileClasspathElements() ) {
            if ( path.equals(ignored) ) continue;
            File file = makeAbsolute(new File(path));
            String name = file.toString();
            if ( file.isDirectory() || ! file.exists() ) {
                name = name + "/";
            }
            URL url = new URL("file", null, name);
            getLog().debug("classpath += " + url);
            urls.add(url);
        }
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }

    protected ApiGen.Builder buildApiGen() throws IOException {
        return new ApiGen.Builder()
          .withOutputDirectory(outputDirectory.toPath())
          .withGuiceModuleName(guiceModuleName)
          .withDefaultPackageName(defaultPackageName);
    }

    @Override
    public void execute() {
        try {
            sourceDirectory = makeAbsolute(sourceDirectory);
            outputDirectory = makeAbsolute(outputDirectory);
            if ( ! sourceDirectory.exists() ) return;
            getLog().debug("Running ApiGen\n\tsourceDirectory=" + sourceDirectory +
                           "\n\toutputDirectory=" + outputDirectory);
            ClassLoader cp = getCompileClassLoader();
            ApiGen apiGen = buildApiGen().build();
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cp);
            for ( org.springframework.core.io.Resource resource : resolver.getResources("classpath*:graphql-apigen-schema/*.graphql{,s}") ) {
                URL url = resource.getURL();
                getLog().debug("Processing "+url);
                apiGen.addForReference(url);
            }
            findGraphql(sourceDirectory, apiGen::addForGeneration);
            apiGen.generate();
            Resource schemaResource = new Resource();
            schemaResource.setTargetPath("graphql-apigen-schema");
            schemaResource.setFiltering(false);
            schemaResource.setIncludes(Arrays.asList("*.graphqls","*.graphql"));
            schemaResource.setDirectory(sourceDirectory.toString());
            project.addResource(schemaResource);
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        } catch (Exception e) {
            String msg = e.getMessage();
            if ( null == msg ) msg = e.getClass().getName();
            getLog().error(msg + " when trying to build sources from graphql.", e);
        }
    }

    private interface VisitPath {
        public void visit(Path path) throws IOException;
    }

    private void findGraphql(File rootDir, VisitPath visitPath) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.graphql{,s}");
        Files.walkFileTree(rootDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if ( matcher.matches(file) ) {
                        getLog().debug("Processing "+file);
                        visitPath.visit(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
    }
}
