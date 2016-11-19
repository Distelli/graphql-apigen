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

/**
 * These comments are used to generate plugin.xml. See http://maven.apache.org/plugin-tools/maven-plugin-tools-java/index.html
 *
 * 
 * @goal generate-sources
 * @phase generate-sources
 */
@Mojo(name="apigen",
      defaultPhase=LifecyclePhase.GENERATE_SOURCES)
@Execute(goal="apigen",
         phase = LifecyclePhase.GENERATE_SOURCES)
public class ApiGenMojo extends AbstractMojo {
    /**
     * @parameter property="project"
     * @required
     * @readonly
     */
    @Parameter(defaultValue="${project}", readonly=true)
    private MavenProject project;

    /**
     * Sources
     *
     * @parameter
     * @required
     */
    @Parameter(name="sources", required=true)
    private List<String> sources;

    // TODO: Support "reference" imports when schemas depend on each other.

    /**
     * @parameter default-value="target/generated-sources/apigen"
     * @required
     */
    @Parameter(name="outputDirectory",
               defaultValue="target/generated-sources/apigen")
    private File outputDirectory;

    @Override
    public void execute() {
        if ( null == sources ) {
            getLog().error("The graphql-apigen plugin must contain configuration as such:\n"+
                           "\t<configuration>\n"+
                           "\t  <sources>\n"+
                           "\t    <source>directory-name</source>\n"+
                           "\t  </sources>\n"+
                           "\t</configuration>");
            return;
        }
        try {
            ApiGen apiGen = new ApiGen.Builder()
                .withOutputDirectory(outputDirectory.toPath())
                .build();
            for ( String source : sources ) {
                findGraphql(source, apiGen::addForGeneration);
            }
            apiGen.generate();
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

    private void findGraphql(String rootDir, VisitPath visitPath) throws IOException {
        Path path = FileSystems.getDefault().getPath(rootDir);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.graphql");
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if ( matcher.matches(file) ) {
                        visitPath.visit(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
    }
}
