/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.test.junit6.patterns;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reproducer for https://github.com/apache/camel-quarkus/issues/7140
 *
 * Root cause: when isUseAdviceWith()=true, routes from createRouteBuilder() are
 * added as definitions but NOT started (correct per adviceWith contract). The user
 * applies advice in doBeforeEach() and then calls startRouteDefinitions() to start
 * the advised routes. However, CamelQuarkusTestSupport.createdRoutes is computed in
 * doPostSetup() BEFORE startRouteDefinitions() is called, so the route set is empty.
 * When resetContext() runs after the test, it finds no routes to clean up.
 *
 * Consequence: advised routes accumulate across test methods. By the second test,
 * the route from the first test is still running with its consumer active, and a
 * NEW route definition is added on top. This means the consumer starts BEFORE
 * advice can be applied (violating the isUseAdviceWith() contract).
 *
 * With a real Kafka consumer (as in the original report), this causes the consumer
 * to attempt connecting to the broker before advice can replace it with a mock.
 * The route coverage flag (isDumpRouteCoverage) is included as it was part of the
 * original report, though the route accumulation bug is independent of it.
 */
@QuarkusTest
@TestProfile(AdviceWithRouteCoverageTest.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdviceWithRouteCoverageTest extends CamelQuarkusTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isDumpRouteCoverage() {
        return true;
    }

    @Override
    protected void doBeforeEach(QuarkusTestMethodContext context) throws Exception {
        AdviceWith.adviceWith(this.context, "consumerRoute", route -> {
            route.replaceFromWith("direct:test");
        });
        startRouteDefinitions();
    }

    @Test
    @Order(1)
    public void testFirstAdvicedRoute() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedBodiesReceived("Hello World");

        template.sendBody("direct:test", "Hello World");

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    @Order(2)
    public void testSecondAdvicedRoute() throws Exception {
        long routeCount = context.getRoutes().stream()
                .filter(r -> "consumerRoute".equals(r.getRouteId()))
                .count();

        assertEquals(1, routeCount,
                "Route accumulation: expected 1 route 'consumerRoute' but found " + routeCount
                        + ". Routes started via startRouteDefinitions() are not tracked by "
                        + "CamelQuarkusTestSupport.createdRoutes, so resetContext() does not "
                        + "clean them up between tests. The consumer from the previous test "
                        + "is already active before advice can be applied.");

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedBodiesReceived("Goodbye World");

        template.sendBody("direct:test", "Goodbye World");

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:input").routeId("consumerRoute")
                        .log("Processing: ${body}")
                        .to("mock:result");
            }
        };
    }
}
