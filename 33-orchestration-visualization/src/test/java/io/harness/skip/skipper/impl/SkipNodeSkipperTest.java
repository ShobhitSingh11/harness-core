package io.harness.skip.skipper.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.skip.SkipType.NOOP;
import static io.harness.skip.SkipType.SKIP_NODE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import io.harness.OrchestrationVisualizationTest;
import io.harness.beans.EdgeList;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationAdjacencyList;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationGraph;
import io.harness.execution.status.Status;
import io.harness.rule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SkipNodeSkipperTest extends OrchestrationVisualizationTest {
  private static final String PLAN_EXECUTION_ID = generateUuid();

  @Inject private SkipNodeSkipper skipNodeSkipper;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldRemoveSubgraphWithPrevAndNextPresent() {
    GraphVertex previous = GraphVertex.builder().uuid(generateUuid()).name("previous").skipType(NOOP).build();
    GraphVertex current = GraphVertex.builder().uuid(generateUuid()).name("current").skipType(SKIP_NODE).build();
    GraphVertex next = GraphVertex.builder().uuid(generateUuid()).name("next").skipType(NOOP).build();
    GraphVertex currentChild1 = GraphVertex.builder().uuid(generateUuid()).name("currentChild1").skipType(NOOP).build();
    GraphVertex currentChild2 = GraphVertex.builder().uuid(generateUuid()).name("currentChild2").skipType(NOOP).build();
    List<GraphVertex> graphVertices = Lists.newArrayList(previous, current, next, currentChild1, currentChild2);

    Map<String, GraphVertex> graphVertexMap =
        graphVertices.stream().collect(Collectors.toMap(GraphVertex::getUuid, Function.identity()));
    Map<String, EdgeList> adjacencyList = new HashMap<>();
    adjacencyList.put(previous.getUuid(),
        EdgeList.builder()
            .parentId(null)
            .prevIds(new ArrayList<>())
            .nextIds(Lists.newArrayList(current.getUuid()))
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(current.getUuid(),
        EdgeList.builder()
            .parentId(null)
            .prevIds(Lists.newArrayList(previous.getUuid()))
            .nextIds(Lists.newArrayList(next.getUuid()))
            .edges(Lists.newArrayList(currentChild1.getUuid(), currentChild2.getUuid()))
            .build());
    adjacencyList.put(next.getUuid(),
        EdgeList.builder()
            .parentId(null)
            .prevIds(Lists.newArrayList(current.getUuid()))
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild1.getUuid(),
        EdgeList.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild2.getUuid(),
        EdgeList.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());

    OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                .startTs(System.currentTimeMillis())
                                                .endTs(System.currentTimeMillis())
                                                .status(Status.SUCCEEDED)
                                                .rootNodeIds(Collections.singletonList("someId"))
                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                .adjacencyList(OrchestrationAdjacencyList.builder()
                                                                   .graphVertexMap(graphVertexMap)
                                                                   .adjacencyList(adjacencyList)
                                                                   .build())
                                                .build();

    skipNodeSkipper.skip(orchestrationGraph, current);

    OrchestrationAdjacencyList updatedOrchestrationAdjacencyList = orchestrationGraph.getAdjacencyList();
    assertThat(updatedOrchestrationAdjacencyList).isNotNull();

    Map<String, GraphVertex> updatedGraphVertexMap = updatedOrchestrationAdjacencyList.getGraphVertexMap();
    assertThat(updatedGraphVertexMap).isNotEmpty();
    assertThat(updatedGraphVertexMap.values()).containsExactlyInAnyOrder(previous, currentChild1, currentChild2, next);

    Map<String, EdgeList> updatedAdjacencyList = updatedOrchestrationAdjacencyList.getAdjacencyList();
    assertThat(updatedAdjacencyList).isNotEmpty();
    assertThat(updatedAdjacencyList.keySet())
        .containsExactlyInAnyOrder(
            previous.getUuid(), currentChild1.getUuid(), currentChild2.getUuid(), next.getUuid());

    assertThat(updatedAdjacencyList.get(previous.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(currentChild1.getUuid(), currentChild2.getUuid());
    assertThat(updatedAdjacencyList.get(previous.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(previous.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(previous.getUuid()).getPrevIds()).isEmpty();

    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(next.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getPrevIds())
        .containsExactlyInAnyOrder(previous.getUuid());

    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(next.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getPrevIds())
        .containsExactlyInAnyOrder(previous.getUuid());

    assertThat(updatedAdjacencyList.get(next.getUuid()).getNextIds()).isEmpty();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getPrevIds())
        .containsExactlyInAnyOrder(currentChild1.getUuid(), currentChild2.getUuid());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldPromoteChildrenWhenCurrentHasParentAndNext() {
    GraphVertex parent = GraphVertex.builder().uuid(generateUuid()).name("parent").skipType(NOOP).build();
    GraphVertex current = GraphVertex.builder().uuid(generateUuid()).name("current").skipType(SKIP_NODE).build();
    GraphVertex next = GraphVertex.builder().uuid(generateUuid()).name("next").skipType(NOOP).build();
    GraphVertex currentChild1 = GraphVertex.builder().uuid(generateUuid()).name("currentChild1").skipType(NOOP).build();
    GraphVertex currentChild2 = GraphVertex.builder().uuid(generateUuid()).name("currentChild2").skipType(NOOP).build();
    List<GraphVertex> graphVertices = Lists.newArrayList(parent, current, next, currentChild1, currentChild2);

    Map<String, GraphVertex> graphVertexMap =
        graphVertices.stream().collect(Collectors.toMap(GraphVertex::getUuid, Function.identity()));
    Map<String, EdgeList> adjacencyList = new HashMap<>();
    adjacencyList.put(parent.getUuid(),
        EdgeList.builder()
            .parentId(null)
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(Lists.newArrayList(current.getUuid()))
            .build());
    adjacencyList.put(current.getUuid(),
        EdgeList.builder()
            .parentId(parent.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(Lists.newArrayList(next.getUuid()))
            .edges(Lists.newArrayList(currentChild1.getUuid(), currentChild2.getUuid()))
            .build());
    adjacencyList.put(next.getUuid(),
        EdgeList.builder()
            .parentId(null)
            .prevIds(Lists.newArrayList(current.getUuid()))
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild1.getUuid(),
        EdgeList.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild2.getUuid(),
        EdgeList.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());

    OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                .startTs(System.currentTimeMillis())
                                                .endTs(System.currentTimeMillis())
                                                .status(Status.SUCCEEDED)
                                                .rootNodeIds(Collections.singletonList("someId"))
                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                .adjacencyList(OrchestrationAdjacencyList.builder()
                                                                   .graphVertexMap(graphVertexMap)
                                                                   .adjacencyList(adjacencyList)
                                                                   .build())
                                                .build();

    skipNodeSkipper.skip(orchestrationGraph, current);

    OrchestrationAdjacencyList updatedOrchestrationAdjacencyList = orchestrationGraph.getAdjacencyList();
    assertThat(updatedOrchestrationAdjacencyList).isNotNull();

    Map<String, GraphVertex> updatedGraphVertexMap = updatedOrchestrationAdjacencyList.getGraphVertexMap();
    assertThat(updatedGraphVertexMap).isNotEmpty();
    assertThat(updatedGraphVertexMap.values()).containsExactlyInAnyOrder(parent, currentChild1, currentChild2, next);

    Map<String, EdgeList> updatedAdjacencyList = updatedOrchestrationAdjacencyList.getAdjacencyList();
    assertThat(updatedAdjacencyList).isNotEmpty();
    assertThat(updatedAdjacencyList.keySet())
        .containsExactlyInAnyOrder(parent.getUuid(), currentChild1.getUuid(), currentChild2.getUuid(), next.getUuid());

    assertThat(updatedAdjacencyList.get(parent.getUuid()).getNextIds()).isEmpty();
    assertThat(updatedAdjacencyList.get(parent.getUuid()).getEdges())
        .containsExactlyInAnyOrder(currentChild1.getUuid(), currentChild2.getUuid());
    assertThat(updatedAdjacencyList.get(parent.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(parent.getUuid()).getPrevIds()).isEmpty();

    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(next.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getParentId()).isEqualTo(parent.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getPrevIds()).isEmpty();

    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(next.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getParentId()).isEqualTo(parent.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getPrevIds()).isEmpty();

    assertThat(updatedAdjacencyList.get(next.getUuid()).getNextIds()).isEmpty();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getPrevIds())
        .containsExactlyInAnyOrder(currentChild1.getUuid(), currentChild2.getUuid());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldRemoveSubgraphWhenCurrentIsRootNode() {
    GraphVertex current = GraphVertex.builder().uuid(generateUuid()).name("current").skipType(SKIP_NODE).build();
    GraphVertex next = GraphVertex.builder().uuid(generateUuid()).name("next").skipType(NOOP).build();
    GraphVertex currentChild1 = GraphVertex.builder().uuid(generateUuid()).name("currentChild1").skipType(NOOP).build();
    GraphVertex currentChild2 = GraphVertex.builder().uuid(generateUuid()).name("currentChild2").skipType(NOOP).build();
    List<GraphVertex> graphVertices = Lists.newArrayList(current, next, currentChild1, currentChild2);

    Map<String, GraphVertex> graphVertexMap =
        graphVertices.stream().collect(Collectors.toMap(GraphVertex::getUuid, Function.identity()));
    Map<String, EdgeList> adjacencyList = new HashMap<>();
    adjacencyList.put(current.getUuid(),
        EdgeList.builder()
            .parentId(null)
            .prevIds(new ArrayList<>())
            .nextIds(Lists.newArrayList(next.getUuid()))
            .edges(Lists.newArrayList(currentChild1.getUuid(), currentChild2.getUuid()))
            .build());
    adjacencyList.put(next.getUuid(),
        EdgeList.builder()
            .parentId(null)
            .prevIds(Lists.newArrayList(current.getUuid()))
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild1.getUuid(),
        EdgeList.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild2.getUuid(),
        EdgeList.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());

    OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                .startTs(System.currentTimeMillis())
                                                .endTs(System.currentTimeMillis())
                                                .status(Status.SUCCEEDED)
                                                .rootNodeIds(Lists.newArrayList(current.getUuid()))
                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                .adjacencyList(OrchestrationAdjacencyList.builder()
                                                                   .graphVertexMap(graphVertexMap)
                                                                   .adjacencyList(adjacencyList)
                                                                   .build())
                                                .build();

    skipNodeSkipper.skip(orchestrationGraph, current);

    assertThat(orchestrationGraph.getRootNodeIds())
        .containsExactlyInAnyOrder(currentChild1.getUuid(), currentChild2.getUuid());

    OrchestrationAdjacencyList updatedOrchestrationAdjacencyList = orchestrationGraph.getAdjacencyList();
    assertThat(updatedOrchestrationAdjacencyList).isNotNull();

    Map<String, GraphVertex> updatedGraphVertexMap = updatedOrchestrationAdjacencyList.getGraphVertexMap();
    assertThat(updatedGraphVertexMap).isNotEmpty();
    assertThat(updatedGraphVertexMap.values()).containsExactlyInAnyOrder(currentChild1, currentChild2, next);

    Map<String, EdgeList> updatedAdjacencyList = updatedOrchestrationAdjacencyList.getAdjacencyList();
    assertThat(updatedAdjacencyList).isNotEmpty();
    assertThat(updatedAdjacencyList.keySet())
        .containsExactlyInAnyOrder(currentChild1.getUuid(), currentChild2.getUuid(), next.getUuid());

    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(next.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(currentChild1.getUuid()).getPrevIds()).isEmpty();

    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(next.getUuid());
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(currentChild2.getUuid()).getPrevIds()).isEmpty();

    assertThat(updatedAdjacencyList.get(next.getUuid()).getNextIds()).isEmpty();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(next.getUuid()).getPrevIds())
        .containsExactlyInAnyOrder(currentChild1.getUuid(), currentChild2.getUuid());
  }
}
