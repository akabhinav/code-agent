package com.joz.common.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/** Utility for building JSON Schema objects for tool definitions. */
public final class ToolSchemaBuilder {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ToolSchemaBuilder() {}

    /** Creates an object schema with the given properties. */
    public static ObjectNode objectSchema(List<Property> properties, List<String> required) {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        for (var prop : properties) {
            var propNode = props.putObject(prop.name());
            propNode.put("type", prop.type());
            propNode.put("description", prop.description());
            if (prop.enumValues() != null) {
                var enumArray = propNode.putArray("enum");
                prop.enumValues().forEach(enumArray::add);
            }
            if (prop.defaultValue() != null) {
                propNode.put("default", prop.defaultValue());
            }
        }
        if (required != null && !required.isEmpty()) {
            var reqArray = schema.putArray("required");
            required.forEach(reqArray::add);
        }
        return schema;
    }

    /** A property in a JSON Schema object. */
    public record Property(
            String name,
            String type,
            String description,
            List<String> enumValues,
            String defaultValue) {

        /** Simple property without enum or default. */
        public static Property of(String name, String type, String description) {
            return new Property(name, type, description, null, null);
        }

        /** Property with enum values. */
        public static Property ofEnum(String name, String description, List<String> values) {
            return new Property(name, "string", description, values, null);
        }

        /** Property with a default value. */
        public static Property withDefault(String name, String type, String description, String defaultValue) {
            return new Property(name, type, description, null, defaultValue);
        }
    }
}
