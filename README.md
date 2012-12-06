stanford-nlp-war
================

Provides a REST resource, JMS queue listener, and basic HTML form for making natural language processing parse requests using the Stanford CoreNLP libraries.

This component is designed to communicate with the non-GPL project https://github.com/mothsoft/openalexis "at arms length" to comply with the terms of the GPL.

This module is licensed under the terms of GPLv2. See COPYING for more information.


Building
==============
This software is built with Maven. Issue 'mvn clean install' at the root of your checkout.

Installation
==============
Push the WAR to any Java servlet container as stanford.war

Executing
=============
* Simple HTML test: Browse to /stanford and provide a text document via the HTML form
* REST: POST a text document to /stanford/parser
* JMS: Mainly an integration point for OpenAlexis, but you can reverse engineer the queue and message format by reviewing ParseRequestMessageListener.java
