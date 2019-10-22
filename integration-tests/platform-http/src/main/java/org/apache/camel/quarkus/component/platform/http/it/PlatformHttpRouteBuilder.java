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
package org.apache.camel.quarkus.component.platform.http.it;

import java.io.ByteArrayOutputStream;

import javax.activation.DataHandler;

import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;

public class PlatformHttpRouteBuilder extends RouteBuilder {
    @Override
    public void configure() {
        rest()
            .get("/platform-http/rest-get")
                .route()
                .setBody(constant("GET: /rest-get"))
                .endRest()
            .post("/platform-http/rest-post")
                .route()
                .setBody(constant("POST: /rest-post"))
                .endRest();

        from("platform-http:/platform-http/hello?httpMethodRestrict=GET").setBody(simple("Hello ${header.name}"));
        from("platform-http:/platform-http/get-post?httpMethodRestrict=GET,POST").setBody(simple("Hello ${body}"));
        from("platform-http:/platform-http/multipart?httpMethodRestrict=POST")
            .to("log:multipart")
            .process(e -> {
                final AttachmentMessage am = e.getMessage(AttachmentMessage.class);
                final DataHandler src = am.getAttachment("bytes.bin");
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    src.writeTo(out);
                    e.getMessage().setBody(out.toByteArray());
                }
            });
    }
}
