/* Dev Tools for Arabic and LanguageTool
 * Copyright (C) 2020 Sohaib Afifi
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.dev.bigdata;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.languagetool.Language;
import org.languagetool.UserConfig;
import org.languagetool.chunking.Chunker;
import org.languagetool.language.Contributor;
import org.languagetool.languagemodel.LanguageModel;
import org.languagetool.rules.Rule;
import org.languagetool.tagging.Tagger;
import org.languagetool.tagging.xx.DemoTagger;
import org.languagetool.tokenizers.SentenceTokenizer;
import org.languagetool.tokenizers.Tokenizer;
import org.languagetool.tokenizers.WordTokenizer;

import java.io.*;
import java.util.*;

/**
 * Indexing a text file to ngrams.
 *
 * @author Sohaib Afifi
 * adapted from CommonCrawlToNgram
 * @since 4.9
 */
public class TextToNgram implements AutoCloseable {

    private static final int MAX_TOKEN_LENGTH = 20;

    private final File input;
    private final File indexTopDir;
    private final SentenceTokenizer sentenceTokenizer;
    private final Tokenizer wordTokenizer;
    private final Map<String, Long> unigramToCount = new HashMap<>();
    private final Map<String, Long> bigramToCount = new HashMap<>();
    private final Map<String, Long> trigramToCount = new HashMap<>();
    private final Map<Integer, LuceneLiveIndex> indexes = new HashMap<>();
    private final Language language = new Arabic();

    private int cacheLimit = 1_000_000;  // max. number of trigrams in HashMap before we flush to Lucene
    private long charCount = 0;
    private long lineCount = 0;
    private long lineFreq = 1;

    public TextToNgram(File input, File indexTopDir) throws IOException {
        this.input = input;
        this.indexTopDir = indexTopDir;
        this.sentenceTokenizer = language.getSentenceTokenizer();
        this.wordTokenizer = new ArabicWordTokenizer();
        indexes.put(1, new LuceneLiveIndex(new File(indexTopDir, "1grams")));
        indexes.put(2, new LuceneLiveIndex(new File(indexTopDir, "2grams")));
        indexes.put(3, new LuceneLiveIndex(new File(indexTopDir, "3grams")));
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.out.println("Usage: " + TextToNgram.class + "<input.txt> <outputDir>");
            System.exit(1);
        }
        File input = new File(args[1]);
        File outputDir = new File(args[2] + "/ar/");
        try (TextToNgram prg = new TextToNgram(input, outputDir)) {
            prg.indexInputFile();
        }
    }

    @Override
    public void close() throws IOException {
        for (LuceneLiveIndex index : indexes.values()) {
            index.close();
        }
    }

    public void setCacheLimit(int cacheLimit) {
        this.cacheLimit = cacheLimit;
    }

    public void indexInputFile() throws IOException {
        write();  // run now so we have a baseline
        FileInputStream fin = new FileInputStream(input);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fin));

        String line = reader.readLine();
        while (line != null) {
            indexLine(line);
            line = reader.readLine();
        }

        write();
    }

    private void indexLine(String line) throws IOException {
        if (lineCount++ % 50_000 == 0) {
            float mb = (float) charCount / 1000 / 1000;
            System.out.printf(Locale.ENGLISH, "Indexing line %d (%.2fMB)\n", lineCount, mb);
        }
        if (lineCount % lineFreq != 0) {
            return;
        }
        charCount += line.length();

        List<String> sentences = sentenceTokenizer.tokenize(line);
        for (String sentence : sentences) {
            indexSentence(sentence);
        }
    }

    private void indexSentence(String sentence) throws IOException {
        List<String> tokens = wordTokenizer.tokenize(sentence);
        tokens.add(0, LanguageModel.GOOGLE_SENTENCE_START);
        tokens.add(LanguageModel.GOOGLE_SENTENCE_END);
        String prevPrev = null;
        String prev = null;
        for (String token : tokens) {
            if (token.trim().isEmpty()) {
                continue;
            }
            if (token.length() <= MAX_TOKEN_LENGTH) {
                unigramToCount.compute(token, (k, v) -> v == null ? 1 : v + 1);
            }
            if (prev != null) {
                if (token.length() <= MAX_TOKEN_LENGTH && prev.length() <= MAX_TOKEN_LENGTH) {
                    String ngram = prev + " " + token;
                    bigramToCount.compute(ngram, (k, v) -> v == null ? 1 : v + 1);
                }
            }
            if (prevPrev != null && prev != null) {
                if (token.length() <= MAX_TOKEN_LENGTH && prev.length() <= MAX_TOKEN_LENGTH && prevPrev.length() <= MAX_TOKEN_LENGTH) {
                    String ngram = prevPrev + " " + prev + " " + token;
                    trigramToCount.compute(ngram, (k, v) -> v == null ? 1 : v + 1);
                }
                if (trigramToCount.size() > cacheLimit) {
                    write();
                }
            }
            prevPrev = prev;
            prev = token;
        }
    }

    private void write() throws IOException {
        writeToLucene(1, unigramToCount);
        writeToLucene(2, bigramToCount);
        writeToLucene(3, trigramToCount);
    }

    private void writeToLucene(int ngramSize, Map<String, Long> ngramToCount) throws IOException {
        long startTime = System.currentTimeMillis();
        System.out.println("Writing " + ngramToCount.size() + " cached ngrams to Lucene index (ngramSize=" + ngramSize + ")...");
        LuceneLiveIndex index = indexes.get(ngramSize);
        index.reader = DirectoryReader.open(index.indexWriter, true);
        index.searcher = new IndexSearcher(index.reader);
        for (Map.Entry<String, Long> entry : ngramToCount.entrySet()) {
            Term ngram = new Term("ngram", entry.getKey());
            TopDocs topDocs = index.searcher.search(new TermQuery(ngram), 2);
            //System.out.println(ngram + " ==> " + topDocs.totalHits);
            if (topDocs.totalHits == 0) {
                Document doc = getDoc(entry.getKey(), entry.getValue());
                index.indexWriter.addDocument(doc);
            } else if (topDocs.totalHits == 1) {
                int docNumber = topDocs.scoreDocs[0].doc;
                Document document = index.reader.document(docNumber);
                long oldCount = Long.parseLong(document.getField("count").stringValue());
                index.indexWriter.deleteDocuments(ngram);
                index.indexWriter.addDocument(getDoc(entry.getKey(), oldCount + entry.getValue()));
                // would probably be faster, but we currently rely on the count being a common field:
                //indexWriter.updateNumericDocValue(ngram, "count", oldCount + entry.getValue());
            } else if (topDocs.totalHits > 1) {
                throw new RuntimeException("Got more than one hit for: " + ngram);
            }
        }
        if (ngramSize == 1) {
            // TODO: runtime code will crash if there are more than 1000 of these docs, so update instead of delete
            long total = ngramToCount.values().stream().mapToLong(Number::longValue).sum();
            System.out.println("Adding totalTokenCount doc: " + total);
            addTotalTokenCountDoc(total, index.indexWriter);
        }
        System.out.println("Commit...");
        index.indexWriter.commit();
        System.out.println("Commit done, indexing took " + (System.currentTimeMillis() - startTime) + "ms");
        ngramToCount.clear();
    }

    @NotNull
    private Document getDoc(String ngram, long count) {
        Document doc = new Document();
        doc.add(new Field("ngram", ngram, StringField.TYPE_NOT_STORED));
        doc.add(getCountField(count));
        return doc;
    }

    @NotNull
    private LongField getCountField(long count) {
        FieldType fieldType = new FieldType();
        fieldType.setStored(true);
        fieldType.setOmitNorms(true);
        fieldType.setNumericType(FieldType.NumericType.LONG);
        fieldType.setDocValuesType(DocValuesType.NUMERIC);
        return new LongField("count", count, fieldType);
    }

    private void addTotalTokenCountDoc(long totalTokenCount, IndexWriter writer) throws IOException {
        FieldType fieldType = new FieldType();
        fieldType.setIndexOptions(IndexOptions.DOCS);
        fieldType.setStored(true);
        fieldType.setOmitNorms(true);
        Field countField = new Field("totalTokenCount", String.valueOf(totalTokenCount), fieldType);
        Document doc = new Document();
        doc.add(countField);
        writer.addDocument(doc);
    }

    static class LuceneLiveIndex {

        private final Directory directory;
        private final IndexWriter indexWriter;

        private DirectoryReader reader;
        private IndexSearcher searcher;

        LuceneLiveIndex(File dir) throws IOException {
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            directory = FSDirectory.open(dir.toPath());
            indexWriter = new IndexWriter(directory, config);
            reader = DirectoryReader.open(indexWriter, false);
            searcher = new IndexSearcher(reader);
        }

        void close() throws IOException {
            reader.close();
            indexWriter.close();
            directory.close();
        }

    }

    /**
     * @since 4.9
     */
    public static class ArabicWordTokenizer extends WordTokenizer {

        @Override
        public String getTokenizingCharacters() {
            return super.getTokenizingCharacters() + "،؟؛";
        }
    }


    /*
     * Keep here for now since the arabic package is not published yet
     */
    public class Arabic extends Language implements AutoCloseable {

        private WordTokenizer wordTokenizer;
        private DemoTagger tagger;
        private LanguageModel languageModel;

        @Override
        public String getName() {
            return "Arabic";
        }

        @Override
        public String getShortCode() {
            return "ar";
        }

        @Override
        public String[] getCountries() {
            return new String[]{"", "SA", "DZ", "BH", "EG", "IQ", "JO", "KW", "LB", "LY", "MA", "OM", "QA", "SD", "SY", "TN", "AE", "YE"};
        }

        @Nullable
        @Override
        public Contributor[] getMaintainers() {
            return new Contributor[0];
        }


        @Override
        public WordTokenizer getWordTokenizer() {
            if (wordTokenizer == null) {
                wordTokenizer = new TextToNgram.ArabicWordTokenizer();
            }
            return wordTokenizer;
        }


        @Override
        public List<Rule> getRelevantRules(ResourceBundle messages, UserConfig userConfig, Language motherTongue, List<Language> altLanguages) throws IOException {
            return Arrays.asList(

            );
        }

        @Override
        public Tagger getTagger() {
            if (tagger == null) {
                tagger = new DemoTagger();
            }
            return tagger;
        }

        @Override
        public Chunker getChunker() {
            return null;
        }

        @Override
        public synchronized LanguageModel getLanguageModel(File indexDir) throws IOException {
            languageModel = initLanguageModel(indexDir, languageModel);
            return languageModel;
        }

        @Override
        public void close() throws Exception {
            if (languageModel != null) {
                languageModel.close();
            }
        }
    }

}
