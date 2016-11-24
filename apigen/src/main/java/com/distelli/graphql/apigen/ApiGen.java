package com.distelli.graphql.apigen;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.parser.Parser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import java.util.List;
import java.util.ArrayList;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ApiGen {
    private Parser parser = new Parser();
    private Path outputDirectory;
    private STGroup stGroup;
    private String guiceModuleName;
    private Map<String, TypeEntry> generatedTypes = new LinkedHashMap<>();
    private Map<String, TypeEntry> referenceTypes = new HashMap<>();
    private List<TypeEntry> schemaDefinitions = new ArrayList<>();

    public static class Builder {
        private Path outputDirectory;
        private STGroup stGroup;
        private String guiceModuleName;

        /**
         * (required)
         *
         * @param outputDirectory is the location of where the .java files are written.
         *
         * @return this
         */
        public Builder withOutputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        /**
         * @param stGroup is used for specifying a custom template group.
         *        See graphql-apigen.stg for an example of what templates must be
         *        specified.
         *
         * @return this
         */
        public Builder withSTGroup(STGroup stGroup) {
            this.stGroup = stGroup;
            return this;
        }

        public Builder withGuiceModuleName(String guiceModuleName) {
            this.guiceModuleName = guiceModuleName;
            return this;
        }

        /**
         * Create a new instances of ApiGen with the built parameters.
         *
         * @return the new ApiGen instance.
         *
         * @throws IOException if an io error occurs.
         */
        public ApiGen build() throws IOException {
            return new ApiGen(this);
        }
    }

    private ApiGen(Builder builder) throws IOException {
        if ( null == builder.outputDirectory ) {
            throw new NullPointerException("The ApiGen outputDirectory must be specified");
        }
        guiceModuleName = builder.guiceModuleName;
        outputDirectory = builder.outputDirectory;
        stGroup = ( null == builder.stGroup )
            ? getDefaultSTGroup()
            : builder.stGroup;
    }

    /**
     * Add a graphql schema document used for reference, but no code generation.
     *
     * @param path is the path to a graphql file to add for reference purposes.
     *
     * @throws IOException if an io error occurs.
     */
    public void addForReference(URL path) throws IOException {
        add(referenceTypes, path);
    }

    public void addForReference(Path path) throws IOException {
        addForReference(path.toFile().toURI().toURL());
    }

    /**
     * Add a graphql schema document.
     *
     * @param path the location of the graphql document.
     *
     * @throws IOException if an io error occurs.
     */
    public void addForGeneration(URL path) throws IOException {
        add(generatedTypes, path);
    }

    public void addForGeneration(Path path) throws IOException {
        addForGeneration(path.toFile().toURI().toURL());
    }

    private void add(Map<String, TypeEntry> types, URL path) throws IOException {
        String content = slurp(path);
        try {
            Document doc = parser.parseDocument(content);
            for ( Definition definition : doc.getDefinitions() ) {
                if ( definition instanceof SchemaDefinition ) {
                    if ( generatedTypes == types ) {
                        schemaDefinitions.add(new TypeEntry(definition, path));
                    }
                    continue;
                } else if ( ! (definition instanceof TypeDefinition) ) {
                    // TODO: What about @definition types?
                    throw new RuntimeException(
                        "GraphQL schema documents must only contain schema type definitions, got "+
                        definition.getClass().getSimpleName() + " [" +
                        definition.getSourceLocation().getLine() + "," +
                        definition.getSourceLocation().getColumn() + "]");
                }
                TypeEntry newEntry = new TypeEntry(definition, path);
                TypeEntry oldEntry = referenceTypes.get(newEntry.getName());

                if ( null != oldEntry ) {
                    // TODO: Support the extend type?
                    throw new RuntimeException(
                        "Duplicate type definition for '" + newEntry.getName() + "'" +
                        " defined both in " + oldEntry.getSourceLocation() + " and " +
                        newEntry.getSourceLocation());
                }

                types.put(newEntry.getName(), newEntry);
                if ( types != referenceTypes ) {
                    // All types should be added to reference types...
                    referenceTypes.put(newEntry.getName(), newEntry);
                }
            }
        } catch ( Exception ex ) {
            throw new RuntimeException(ex.getMessage() + " when parsing '"+path+"'", ex);
        }
    }

    /**
     * Generate the graphql APIs (and DataFetcher adaptors).
     *
     * @throws IOException if an io error occurs.
     */
    public void generate() throws IOException {
        Set<String> generatorNames = new TreeSet<String>();
        for ( String name : stGroup.getTemplateNames() ) {
            if ( ! name.endsWith("FileName") ) continue;
            String generatorName = name.substring(0, name.length() - "FileName".length());
            if ( ! stGroup.isDefined(generatorName + "Generator") ) continue;
            generatorNames.add(generatorName);
        }

        List<TypeEntry> allEntries = new ArrayList(generatedTypes.values());
        allEntries.addAll(schemaDefinitions);
        StringBuilder moduleBuilder = new StringBuilder();
        for ( TypeEntry entry : allEntries ) {
            try {
                STModel model = new STModel.Builder()
                    .withTypeEntry(entry)
                    .withReferenceTypes(referenceTypes)
                    .build();
                model.validate();

                Path directory = getDirectory(entry.getPackageName());
                for ( String generatorName : generatorNames ) {
                    String fileName = stGroup.getInstanceOf(generatorName+"FileName")
                        .add("model", model)
                        .render();
                    if ( "".equals(fileName) || null == fileName ) continue;
                    String content = stGroup.getInstanceOf(generatorName+"Generator")
                        .add("model", model)
                        .render();
                    if ( stGroup.isDefined(generatorName + "GuiceModule") ) {
                        moduleBuilder.append(stGroup.getInstanceOf(generatorName+"GuiceModule")
                                             .add("model", model)
                                             .render());
                    }
                    writeFile(Paths.get(directory.toString(), fileName),
                              content);
                }
            } catch ( Exception ex ) {
                throw new RuntimeException(ex.getMessage() + " when generating code from '" +
                                           entry.getSource() + "'", ex);
            }
        }
        if ( moduleBuilder.length() > 0 && guiceModuleName != null && stGroup.isDefined("guiceModule") ) {
            PackageClassName packageClassName = getPackageClassName(guiceModuleName);
            String content = stGroup.getInstanceOf("guiceModule")
                .add("packageName", packageClassName.packageName)
                .add("className", packageClassName.className)
                .add("configure", moduleBuilder.toString())
                .render();
            writeFile(Paths.get(getDirectory(packageClassName.packageName).toString(),
                                packageClassName.className+".java"),
                      content);
        }
    }

    private static class PackageClassName {
        public String packageName;
        public String className;
        public PackageClassName(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
        }
    }

    private PackageClassName getPackageClassName(String fullName) {
        int dot = fullName.lastIndexOf(".");
        if ( dot < 0 ) return new PackageClassName(null, fullName);
        return new PackageClassName(fullName.substring(0, dot),
                                    fullName.substring(dot+1));
    }

    public Path getDirectory(String packageName) {
        String[] dirs = packageName.split("\\.");
        return Paths.get(outputDirectory.toString(), dirs);
    }

    private String slurp(URL path) throws IOException {
        Scanner scan = new Scanner(path.openStream()).useDelimiter("\\A");
        return scan.hasNext() ? scan.next() : "";
    }

    private void writeFile(Path path, String content) throws IOException {
        path.getParent().toFile().mkdirs();
        Files.write(path, content.getBytes(UTF_8));
    }

    private STGroup getDefaultSTGroup() throws IOException {
        return new STGroupFile("graphql-apigen.stg");
    }
}
