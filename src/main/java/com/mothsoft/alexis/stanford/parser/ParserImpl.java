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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.WordTokenFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.util.XMLUtils;

class ParserImpl implements Parser {

    private static final String EXCLAMATION = "!";
    private static final String QUESTION_MARK = "?";
    private static final String PERIOD = ".";
    private static final String UTF_8 = "UTF-8";
    private static final String DOCUMENT_XML_OPEN = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><document><sentences>";
    private static final String DOCUMENT_XML_CLOSE = "</names></document>";
    private static final String SENTENCES_XML_CLOSE = "</sentences>";
    private static final String NAME_XML_OPEN_FORMAT = "<name count=\"%d\">";
    private static final String NAMES_XML_OPEN = "<names>";
    private static final String NAME_XML_CLOSE = "</name>";

    private static final Logger logger = Logger.getLogger(ParserImpl.class.getName());

    @SuppressWarnings("rawtypes")
    private static TokenizerFactory TOKENIZER_FACTORY;
    private static String TOKENIZER_OPTIONS;
    private static TreePrint TREE_PRINT;
    private static LexicalizedParser PARSER;
    private static NERClassifierCombiner CLASSIFIER;

    private static final Set<String> DELIMITERS = new HashSet<String>();

    private static final Set<String> NAME_TYPES = new HashSet<String>();

    static {
        DELIMITERS.add(",");
        DELIMITERS.add(";");
        DELIMITERS.add("--");
        DELIMITERS.add("-");

        NAME_TYPES.add("ORGANIZATION");
        NAME_TYPES.add("PERSON");
        NAME_TYPES.add("LOCATION");
        NAME_TYPES.add("MISC");
        init();
    }

    private static void init() {
        synchronized (ParserImpl.class) {
            TOKENIZER_FACTORY = PTBTokenizer.factory(false, new WordTokenFactory());
            TOKENIZER_OPTIONS = "asciiQuotes=true,escapeForwardSlashAsterisk=false";
            TREE_PRINT = new TreePrint("wordsAndTags", "xml", new PennTreebankLanguagePack());
            PARSER = LexicalizedParser
                    .getParserFromSerializedFile("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
            try {
                CLASSIFIER = new NERClassifierCombiner(true, false,
                        CRFClassifier
                                .getClassifier("edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz"));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    ParserImpl() {
        super();
    }

    @SuppressWarnings("unchecked")
    public void parse(final InputStream is, final OutputStream os) throws IOException {

        final long start = System.currentTimeMillis();
        Reader reader = null;
        PrintWriter printWriter = null;
        try {
            reader = new BufferedReader(new InputStreamReader(is, Charset.forName(UTF_8)));
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, Charset.forName(UTF_8)));
            writer.write(DOCUMENT_XML_OPEN);
            printWriter = new PrintWriter(writer);

            final Tokenizer<HasWord> tokenizer = TOKENIZER_FACTORY.getTokenizer(reader, TOKENIZER_OPTIONS);

            final List<HasWord> sentence = new ArrayList<HasWord>();
            final Map<String, Name> names = new LinkedHashMap<String, Name>();

            while (tokenizer.hasNext()) {
                final HasWord word = tokenizer.next();
                sentence.add(word);

                if (PERIOD.equals(word.word()) || QUESTION_MARK.equals(word.word()) || EXCLAMATION.equals(word.word())
                        || isLongSentenceAtLogicalDelimiter(sentence, word) || probablyNotASentence(sentence)) {
                    captureSentence(sentence, printWriter, names, 0);
                    sentence.clear();
                    os.flush();
                }
            }

            if (!sentence.isEmpty()) {
                captureSentence(sentence, printWriter, names, 0);
                sentence.clear();
            }

            writer.write(SENTENCES_XML_CLOSE);

            writer.write(NAMES_XML_OPEN);

            for (final Name name : names.values()) {
                final String openName = String.format(NAME_XML_OPEN_FORMAT, name.count);
                writer.write(openName);
                writer.write(XMLUtils.escapeElementXML(name.getName()));
                writer.write(NAME_XML_CLOSE);
            }

            writer.write(DOCUMENT_XML_CLOSE);
            writer.flush();

        } finally {
            IOUtils.closeQuietly(reader);
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Parsing took: " + ((System.currentTimeMillis() - start) / 1000.00));
        }
    }

    private boolean probablyNotASentence(final List<HasWord> sentence) {
        // sometimes we get random streams of gibberish from web content. If we
        // haven't encountered a real sentence ending at 35 terms, let's call it
        // quits
        return sentence.size() > 35;
    }

    private synchronized void captureSentence(final List<HasWord> sentence, final PrintWriter printWriter,
            final Map<String, Name> names, int tryCount) {
        try {
            final Tree tree = PARSER.parseTree(sentence);
            TREE_PRINT.printTree(tree, printWriter);
        } catch (Exception e) {
            logger.severe("Encountered Exception - may be swallowed OutOfMemoryError in Stanford NLP - will try cleanup!");
            e.printStackTrace(System.err);

            // trying to reinitialize
            reinit();

            if (tryCount > 0) {
                logger.severe("Failed again on try " + tryCount + " will throw hands up and fail");
                throw (new RuntimeException(e));
            } else {
                logger.info("Retrying sentence");
                captureSentence(sentence, printWriter, names, tryCount + 1);
            }

        } catch (OutOfMemoryError oom) {
            logger.severe("Encountered OutOfMemoryError - will try cleanup");
            oom.printStackTrace(System.err);

            // trying to reinitialize
            reinit();

            if (tryCount > 0) {
                logger.severe("Failed again on try " + tryCount + " will throw hands up and fail");
                throw oom;
            } else {
                logger.info("Retrying sentence");
                captureSentence(sentence, printWriter, names, tryCount + 1);
            }
        }
        captureNamedEntities(sentence, names);
    }

    private void reinit() {
        ParserImpl.CLASSIFIER = null;
        ParserImpl.PARSER = null;
        ParserImpl.TOKENIZER_FACTORY = null;
        ParserImpl.TREE_PRINT = null;

        logger.severe("Trying explicit System.gc call against all my better judgment");
        System.gc();

        logger.warning("Now trying to reinitialize Stanford NLP components");
        init();
    }

    private synchronized void captureNamedEntities(final List<HasWord> sentence, final Map<String, Name> names) {
        final List<CoreLabel> labels = CLASSIFIER.classifySentence(sentence);

        final List<String> words = new ArrayList<String>();
        String prevTag = "";

        for (final CoreLabel label : labels) {
            final String answer = label.get(AnswerAnnotation.class);
            final String tag = answer == null ? "" : answer;
            final String word = label.get(TextAnnotation.class);

            if (tag.equals(CLASSIFIER.flags.backgroundSymbol) || (!prevTag.isEmpty() && !tag.equals(prevTag))) {
                flushWords(words, names);
            }

            if (NAME_TYPES.contains(tag)) {
                words.add(word);
            }
            prevTag = tag;
        }

        flushWords(words, names);

    }

    private void flushWords(List<String> words, Map<String, Name> names) {
        if (!words.isEmpty()) {
            String term = "";
            for (final String ith : words) {
                term += ith + " ";
            }
            term = term.trim();

            if (names.containsKey(term)) {
                names.get(term).increment();
            } else {
                final Name name = new Name(term);
                name.increment();
                names.put(term, name);
            }

            words.clear();
        }
    }

    private boolean isLongSentenceAtLogicalDelimiter(final List<HasWord> sentence, final HasWord word) {
        return sentence.size() >= 25 && DELIMITERS.contains(word.word());
    }

    private class Name {
        private String name;
        private Integer count;

        public Name(final String name) {
            this.name = name;
            count = 0;
        }

        public String getName() {
            return this.name;
        }

        public void increment() {
            count++;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Name other = (Name) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

        private ParserImpl getOuterType() {
            return ParserImpl.this;
        }

    }
}
