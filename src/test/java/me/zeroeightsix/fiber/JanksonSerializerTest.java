package me.zeroeightsix.fiber;

import me.zeroeightsix.fiber.builder.ConfigNodeBuilder;
import me.zeroeightsix.fiber.exception.FiberException;
import me.zeroeightsix.fiber.serialization.JanksonSerializer;
import me.zeroeightsix.fiber.tree.ConfigNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

class JanksonSerializerTest {

    @Test
    @DisplayName("Node -> Node")
    void nodeSerialization() throws IOException, FiberException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JanksonSerializer jk = new JanksonSerializer();
        ConfigNode nodeOne = new ConfigNodeBuilder()
                .beginValue("A", Integer.class)
                .withDefaultValue(10)
                .finishValue()
                .build();

        ConfigNode nodeTwo = new ConfigNodeBuilder()
                .beginValue("A", Integer.class)
                .withDefaultValue(20)
                .finishValue()
                .build();

        jk.serialize(nodeOne, bos);
        jk.deserialize(nodeTwo, new ByteArrayInputStream(bos.toByteArray()));
        NodeOperationsTest.testNodeFor(nodeTwo, "A", Integer.class, 10);
    }

    @Test
    @DisplayName("SubNode -> SubNode")
    void nodeSerialization1() throws IOException, FiberException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JanksonSerializer jk = new JanksonSerializer();
        ConfigNode nodeOne = new ConfigNodeBuilder()
                .fork("child")
                .beginValue("A", 10)
                .finishValue()
                .finishNode()
                .build();

        ConfigNodeBuilder builderTwo = new ConfigNodeBuilder();
        ConfigNode childTwo = builderTwo.fork("child")
                .beginValue("A", 20)
                .finishValue()
                .build();
        ConfigNode nodeTwo = builderTwo.build();

        jk.serialize(nodeOne, bos);
        jk.deserialize(nodeTwo, new ByteArrayInputStream(bos.toByteArray()));
        NodeOperationsTest.testNodeFor(childTwo, "A", Integer.class, 10);
    }

    @Test
    @DisplayName("Ignore SubNode")
    void nodeSerialization2() throws IOException, FiberException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JanksonSerializer jk = new JanksonSerializer();
        ConfigNode parentOne = new ConfigNodeBuilder()
                .fork("child").withSeparateSerialization()
                .beginValue("A", 10)
                .finishValue()
                .build();
        ConfigNodeBuilder builderTwo = new ConfigNodeBuilder();
        ConfigNode childTwo = builderTwo.fork("child").withSeparateSerialization()
                .beginValue("A", 20)
                .finishValue()
                .build();
        ConfigNode parentTwo = builderTwo.build();

        jk.serialize(parentOne, bos);
        jk.deserialize(parentTwo, new ByteArrayInputStream(bos.toByteArray()));
        // the child data should not have been saved -> default value
        NodeOperationsTest.testNodeFor(childTwo, "A", Integer.class, 20);
    }

}