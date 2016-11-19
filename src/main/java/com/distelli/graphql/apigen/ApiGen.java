package com.distelli.graphql.apigen;

import graphql.parser.Parser;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import graphql.language.TypeDefinition;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.ObjectTypeDefinition;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.io.IOException;
import graphql.language.Definition;
import graphql.language.Document;
import java.util.Set;
import java.util.TreeSet;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ApiGen {
    private Parser parser = new Parser();
    private Path outputDirectory;
    private STGroup stGroup;
    private Map<String, TypeEntry> generatedTypes = new LinkedHashMap<>();
    private Map<String, TypeEntry> referenceTypes = new HashMap<>();

    public static class Builder {
        private Path outputDirectory;
        private STGroup stGroup;

        /**
         * (required)
         * @param outputDirectory is the location of where the .java files are written.
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
         * @param this
         */
        public Builder withSTGroup(STGroup stGroup) {
            this.stGroup = stGroup;
            return this;
        }

        /**
         * Create a new instances of ApiGen with the built parameters.
         * @return the new ApiGen instance.
         */
        public ApiGen build() throws IOException {
            return new ApiGen(this);
        }
    }

    private ApiGen(Builder builder) throws IOException {
        if ( null == builder.outputDirectory ) {
            throw new NullPointerException("The ApiGen outputDirectory must be specified");
        }
        outputDirectory = builder.outputDirectory;
        stGroup = ( null == builder.stGroup )
            ? getDefaultSTGroup()
            : builder.stGroup;
    }

    /**
     * Add a graphql schema document used for reference, but no code generation.
     */
    public void addForReference(Path path) throws IOException {
        add(referenceTypes, path);
    }

    /**
     * Add a graphql schema document.
     *
     * @param path the location of the graphql document.
     */
    public void addForGeneration(Path path) throws IOException {
        add(generatedTypes, path);
    }

    private void add(Map<String, TypeEntry> types, Path path) throws IOException {
        String content = slurp(path);
        try {
            Document doc = parser.parseDocument(content);
            for ( Definition definition : doc.getDefinitions() ) {
                if ( ! (definition instanceof TypeDefinition) ) {
                    // TODO: Excessive validation? What about @definition and schema types...
                    throw new RuntimeException(
                        "GraphQL schema documents must only contain schema types, got "+
                        definition.getClass().getSimpleName() + " [" +
                        definition.getSourceLocation().getLine() + "," +
                        definition.getSourceLocation().getColumn() + "]");
                }
                TypeEntry newEntry = new TypeEntry((TypeDefinition)definition, path);
                TypeEntry oldEntry = referenceTypes.get(newEntry.getName());

                if ( null != oldEntry ) {
                    // TODO: Support merging types (especially with the extend type)
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
     */
    public void generate() throws IOException {
        Set<String> generatorNames = new TreeSet<String>();
        for ( String name : stGroup.getTemplateNames() ) {
            if ( ! name.endsWith("FileName") ) continue;
            String generatorName = name.substring(0, name.length() - "FileName".length());
            if ( ! stGroup.isDefined(generatorName + "Generator") ) continue;
            generatorNames.add(generatorName);
        }

        Map<String, StringBuilder> moduleBuilders = new LinkedHashMap<>();
        for ( TypeEntry entry : generatedTypes.values() ) {
            // TODO: Support other types...
            if ( ! entry.isObjectTypeDefinition() ) continue;
            try {
                StringBuilder moduleBuilder = moduleBuilders.get(entry.getPackageName());
                if ( null == moduleBuilder ) {
                    moduleBuilder = new StringBuilder();
                    moduleBuilders.put(entry.getPackageName(), moduleBuilder);
                }

                STModel model = new STModel.Builder()
                    .withTypeEntry(entry)
                    .withReferenceTypes(referenceTypes)
                    .build();

                Path directory = getDirectory(entry.getPackageName());
                for ( String generatorName : generatorNames ) {
                    String fileName = stGroup.getInstanceOf(generatorName+"FileName")
                        .add("model", model)
                        .render();
                    String content = stGroup.getInstanceOf(generatorName+"Generator")
                        .add("model", model)
                        .render();
                    if ( stGroup.isDefined(generatorName + "Guice") ) {
                        moduleBuilder.append(stGroup.getInstanceOf(generatorName+"Guice")
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
        if ( stGroup.isDefined("guiceModule") ) {
            for ( Map.Entry<String, StringBuilder> entry : moduleBuilders.entrySet() ) {
                if ( entry.getValue().length() <= 0 ) continue;
                String content = stGroup.getInstanceOf("GuiceModule")
                    .add("package", entry.getKey())
                    .add("configure", entry.getValue())
                    .render();
                writeFile(Paths.get(getDirectory(entry.getKey()).toString(), "GuiceModule.java"),
                          content);
                                   
            }
        }
    }

    public Path getDirectory(String packageName) {
        String[] dirs = packageName.split("\\.");
        return Paths.get(outputDirectory.toString(), dirs);
    }

    private String slurp(Path path) throws IOException {
        return new String(Files.readAllBytes(path), UTF_8);
    }

    private void writeFile(Path path, String content) throws IOException {
        path.getParent().toFile().mkdirs();
        Files.write(path, content.getBytes(UTF_8));
    }

    private STGroup getDefaultSTGroup() throws IOException {
        return new STGroupFile("graphql-apigen.stg");
    }
}
