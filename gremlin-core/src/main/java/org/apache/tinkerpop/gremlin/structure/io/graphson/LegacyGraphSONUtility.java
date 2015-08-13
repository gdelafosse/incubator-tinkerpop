/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.structure.io.graphson;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.io.IOException;
import java.util.*;

public final class LegacyGraphSONUtility {

    private static final String EMPTY_STRING = "";
    private final Graph g;
    private final Graph.Features.VertexFeatures vertexFeatures;
    private final Graph.Features.EdgeFeatures edgeFeatures;
    private final Map<Object,Vertex> cache;

    public LegacyGraphSONUtility(final Graph g, final Graph.Features.VertexFeatures vertexFeatures,
                                 final Graph.Features.EdgeFeatures edgeFeatures,
                                 final Map<Object, Vertex> cache) {
        this.g = g;
        this.vertexFeatures = vertexFeatures;
        this.edgeFeatures = edgeFeatures;
        this.cache = cache;
    }

    public Vertex vertexFromJson(final JsonNode json) throws IOException {
        final Map<String, Object> props = readProperties(json);

        final Object vertexId = getTypedValueFromJsonNode(json.get(LegacyGraphSONTokens._ID));
        final Vertex v = vertexFeatures.willAllowId(vertexId) ? g.addVertex(T.id, vertexId) : g.addVertex();
        cache.put(vertexId, v);

        for (Map.Entry<String, Object> entry : props.entrySet()) {
            v.property(g.features().vertex().getCardinality(entry.getKey()), entry.getKey(), entry.getValue());
        }

        return v;
    }

    public Edge edgeFromJson(final JsonNode json, final Vertex out, final Vertex in) throws IOException {
        final Map<String, Object> props = LegacyGraphSONUtility.readProperties(json);

        final Object edgeId = getTypedValueFromJsonNode(json.get(LegacyGraphSONTokens._ID));
        final JsonNode nodeLabel = json.get(LegacyGraphSONTokens._LABEL);
        final String label = nodeLabel == null ? EMPTY_STRING : nodeLabel.textValue();

        final Edge e = edgeFeatures.willAllowId(edgeId) ? out.addEdge(label, in, T.id, edgeId) : out.addEdge(label, in) ;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            e.property(entry.getKey(), entry.getValue());
        }

        return e;
    }

    public static Map<String, Object> readProperties(final JsonNode node) {
        final Map<String, Object> map = new HashMap<>();

        final Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> entry = iterator.next();

            if (!isReservedKey(entry.getKey())) {
                // it generally shouldn't be as such but graphson containing null values can't be shoved into
                // element property keys or it will result in error
                final Object o = readProperty(entry.getValue());
                if (o != null) {
                    map.put(entry.getKey(), o);
                }
            }
        }

        return map;
    }

    public static boolean isReservedKey(final String key) {
        return key.equals(LegacyGraphSONTokens._ID) || key.equals(LegacyGraphSONTokens._TYPE) || key.equals(LegacyGraphSONTokens._LABEL)
                || key.equals(LegacyGraphSONTokens._OUT_V) || key.equals(LegacyGraphSONTokens._IN_V)
                || key.equals(LegacyGraphSONTokens._IN_E) || key.equals(LegacyGraphSONTokens._OUT_E);
    }

    private static Object readProperty(final JsonNode node) {
        final Object propertyValue;

        if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_UNKNOWN)) {
            propertyValue = null;
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_BOOLEAN)) {
            propertyValue = node.get(LegacyGraphSONTokens.VALUE).booleanValue();
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_FLOAT)) {
            propertyValue = Float.parseFloat(node.get(LegacyGraphSONTokens.VALUE).asText());
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_BYTE)) {
            propertyValue = Byte.parseByte(node.get(LegacyGraphSONTokens.VALUE).asText());
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_SHORT)) {
            propertyValue = Short.parseShort(node.get(LegacyGraphSONTokens.VALUE).asText());
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_DOUBLE)) {
            propertyValue = node.get(LegacyGraphSONTokens.VALUE).doubleValue();
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_INTEGER)) {
            propertyValue = node.get(LegacyGraphSONTokens.VALUE).intValue();
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_LONG)) {
            propertyValue = node.get(LegacyGraphSONTokens.VALUE).longValue();
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_STRING)) {
            propertyValue = node.get(LegacyGraphSONTokens.VALUE).textValue();
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_LIST)) {
            propertyValue = readProperties(node.get(LegacyGraphSONTokens.VALUE).elements());
        } else if (node.get(LegacyGraphSONTokens._TYPE).textValue().equals(LegacyGraphSONTokens.TYPE_MAP)) {
            propertyValue = readProperties(node.get(LegacyGraphSONTokens.VALUE));
        } else {
            propertyValue = node.textValue();
        }

        return propertyValue;
    }

    private static List readProperties(final Iterator<JsonNode> listOfNodes) {
        final List<Object> array = new ArrayList<>();

        while (listOfNodes.hasNext()) {
            array.add(readProperty(listOfNodes.next()));
        }

        return array;
    }

    static Object getTypedValueFromJsonNode(final JsonNode node) {
        Object theValue = null;

        if (node != null && !node.isNull()) {
            if (node.isBoolean()) {
                theValue = node.booleanValue();
            } else if (node.isDouble()) {
                theValue = node.doubleValue();
            } else if (node.isFloatingPointNumber()) {
                theValue = node.floatValue();
            } else if (node.isInt()) {
                theValue = node.intValue();
            } else if (node.isLong()) {
                theValue = node.longValue();
            } else if (node.isTextual()) {
                theValue = node.textValue();
            } else if (node.isArray()) {
                // this is an array so just send it back so that it can be
                // reprocessed to its primitive components
                theValue = node;
            } else if (node.isObject()) {
                // this is an object so just send it back so that it can be
                // reprocessed to its primitive components
                theValue = node;
            } else {
                theValue = node.textValue();
            }
        }

        return theValue;
    }
}