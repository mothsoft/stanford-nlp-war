/*
    stanford-nlp-war : provides REST, JMS, and HTML form connectivity to Stanford CoreNLP
    Copyright (C) 2012  Tim Garrett, Mothsoft LLC

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.mothsoft.alexis.stanford.jms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.util.StopWatch;

import com.mothsoft.alexis.stanford.parser.Parser;
import com.mothsoft.alexis.stanford.parser.ParserFactory;
import com.mothsoft.alexis.stanford.service.StanfordNLPService;

public class ParseRequestMessageListener implements SessionAwareMessageListener<TextMessage> {

    private static final Logger logger = Logger.getLogger(StanfordNLPService.class.getName());

    private static final String DOCUMENT_ID = "DOCUMENT_ID";
    private static final String EXCEPTION = "EXCEPTION";
    private static final String UTF8 = "UTF-8";

    private final Parser parser;

    public ParseRequestMessageListener() {
        this.parser = ParserFactory.getParser();
        logger.info("Started ParseRequestMessageListener!");
    }

    @Override
    public void onMessage(final TextMessage message, final Session session) throws JMSException {
        final String text = ((TextMessage) message).getText();
        final Destination replyTo = message.getJMSReplyTo();
        final Long documentId = message.getLongProperty(DOCUMENT_ID);
        MessageProducer producer = null;

        final String parsed;
        // parse and handle exceptions (if any) gracefully
        try {
            parsed = parse(documentId, text);
            producer = session.createProducer(replyTo);
        } catch (final Exception e) {
            final TextMessage errorResponse = session.createTextMessage(e.getMessage());
            errorResponse.setLongProperty(DOCUMENT_ID, documentId);
            errorResponse.setBooleanProperty(EXCEPTION, true);
            producer.send(errorResponse);
            return;
        } finally {
            if (producer != null) {
                producer.close();
            }
        }

        // create and send response
        final TextMessage response = session.createTextMessage(parsed);
        response.setLongProperty(DOCUMENT_ID, documentId);
        producer.send(response);
        producer.close();
    }

    private String parse(final long documentId, final String text) {
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        final ByteArrayOutputStream os;
        try {
            final InputStream is = new ByteArrayInputStream(text.getBytes(Charset.forName(UTF8)));
            os = new ByteArrayOutputStream(1024 * 128);
            this.parser.parse(is, os);
            final String result = os.toString(UTF8);

            stopWatch.stop();
            logger.info("Parsing document ID: " + documentId + " took " + stopWatch.getTotalTimeSeconds() + " seconds");

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
