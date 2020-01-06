# languagetool-tools-ar
Dev Tools for Arabic and LanguageTool

Most tools are extracted from [languagetool](http://github.com/languagetool-org/languagetool) and slightly adapted to Arabic. 


## Usage 

1 - download the **languagetool-tools-ar-4.8-jar-with-dependencies.jar** from releases

2 - download the latest arwiki-latest-pages-articles.xml.bz2 from http://dumps.wikimedia.org/arwiki/latest/. 

### Extract sentences from wikipedia dump 

```

java -cp languagetool-tools-ar-4.8-jar-with-dependencies.jar org.languagetool.dev.tools.WikipediaSentenceExtractor arwiki-latest-pages-articles.xml arwiki-latest-pages-articles.txt 
```



### Create an index   

```
mkdir index
java -cp languagetool-tools-ar-4.8-jar-with-dependencies.jar org.languagetool.dev.tools.TextIndexCreator index arwiki-latest-pages-articles.txt 
```


### Generate Lucene Ngrams (1-ngram, 2-ngram, 3-ngram)

 ```
 java -cp languagetool-tools-ar-4.8-jar-with-dependencies.jar org.languagetool.dev.tools.TextToNgram arwiki-latest-pages-articles.txt ngrams
 ```
