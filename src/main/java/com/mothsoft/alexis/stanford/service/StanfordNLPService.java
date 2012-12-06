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
package com.mothsoft.alexis.stanford.service;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.mothsoft.alexis.stanford.parser.Parser;
import com.mothsoft.alexis.stanford.parser.ParserFactory;

@Path("")
public class StanfordNLPService {

    private static final Logger logger = Logger.getLogger(StanfordNLPService.class.getName());

    private Parser parser;

    public StanfordNLPService() {
        this.parser = ParserFactory.getParser();
    }

    @POST
    @Path("/parser")
    @Produces("application/xml")
    public Response parser(@FormParam("content") String content,
            @FormParam("includeAssociations") final boolean includeAssociations) {

        File tempFile = null;
        Writer tempWriter = null;

        try {
            // write to temp file
            tempFile = File.createTempFile("alexis-parser-", "-" + System.currentTimeMillis());
            tempWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile),
                    Charset.forName("UTF-8")));

            IOUtils.copyLarge(new StringReader(content), tempWriter);
            IOUtils.closeQuietly(tempWriter);
            content = null;
        } catch (IOException e) {
            IOUtils.closeQuietly(tempWriter);
            FileUtils.deleteQuietly(tempFile);
            logger.severe(e.getLocalizedMessage());
            return Response.serverError().build();
        } finally {
            IOUtils.closeQuietly(tempWriter);
        }

        final File tempFile2 = tempFile;
        try {
            return Response.ok(new StreamingOutput() {
                final InputStream is = new BufferedInputStream(new FileInputStream(tempFile2));

                public void write(OutputStream os) throws IOException, WebApplicationException {
                    try {
                        StanfordNLPService.this.parser.parse(is, os);
                        IOUtils.closeQuietly(is);
                    } finally {
                        IOUtils.closeQuietly(is);
                        FileUtils.deleteQuietly(tempFile2);
                    }
                }
            }).build();
        } catch (final Exception e) {
            logger.severe(e.getMessage());
            return Response.serverError().build();
        }
    }
}
