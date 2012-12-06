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
package com.mothsoft.alexis.stanford.parser;

import java.util.logging.Logger;

/**
 * Hide the details of how this works, like in case I ever figure out the
 * thread-pool issue
 * 
 * @author tgarrett
 * 
 */
public class ParserFactory {

    private static final Logger logger = Logger.getLogger(ParserFactory.class.getName());

    private static final Parser parser;

    static {
        logger.info("Initializing Parser");
        parser = new ParserImpl();
    }

    public static Parser getParser() {
        return ParserFactory.parser;
    }

}
