package com.distelli.graphql.apigen;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Mojo(name="asyncapigen",
      defaultPhase=LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution=ResolutionScope.COMPILE)
@Execute(goal="asyncapigen")
public class AsyncApiGenMojo extends ApiGenMojo {

    @Override
    protected ApiGen.Builder buildApiGen() throws IOException {
        return super.buildApiGen().asAsync();
    }
}
