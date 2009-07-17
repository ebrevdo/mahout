/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.classifier.bayes;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.io.DefaultStringifier;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.GenericsUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WikipediaDatasetCreatorMapper extends MapReduceBase implements
    Mapper<LongWritable, Text, Text, Text> {

  private static Set<String> inputCategories = null;
  private static boolean exactMatchOnly = false;

  @Override
  public void map(LongWritable key, Text value,
      OutputCollector<Text, Text> output, Reporter reporter)
      throws IOException {
    String document = value.toString();
    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT);
    StringBuilder contents = new StringBuilder();

    Set<String> categories = new HashSet<String>(findAllCategories(document));
    
    String catMatch = matchCategories(categories);
    
    if(!catMatch.equals("Unknown")){
      document = StringEscapeUtils.unescapeHtml(document.replaceFirst("<text xml:space=\"preserve\">", "").replaceAll("</text>", ""));
      TokenStream stream = analyzer.tokenStream(catMatch, new StringReader(document));
      Token token = new Token();
      while((token = stream.next(token)) != null){
        contents.append(token.termBuffer(), 0, token.termLength()).append(' ');
      }
      output.collect(new Text(catMatch.replace(" ","_")), new Text(contents.toString()));
    }
  }
  
  public static String matchCategories(Set<String> categories)
  {
    if (exactMatchOnly == false) {
      for(String category : categories)
      {
        for(String input: inputCategories){
          if(category.contains(input)){
            return input;

          }
        }
      }
    } else {
      for(String category : categories)
      {
        for(String input: inputCategories){
          if(category.equals(input)){
            return input;

          }
        }
      }
    }
    return "Unknown";
  }
  
  public static List<String> findAllCategories(String document){
    List<String> categories =  new ArrayList<String>();
    int startIndex = 0;
    int categoryIndex;
    
    while((categoryIndex = document.indexOf("[[Category:", startIndex))!=-1)
    {
      categoryIndex+=11;
      int endIndex = document.indexOf("]]", categoryIndex);
      if(endIndex>=document.length() || endIndex < 0) break;
      String category = document.substring(categoryIndex, endIndex);
      categories.add(category.toLowerCase());
      startIndex = endIndex;
    }
    
    return categories;
  }
  
  @Override
  public void configure(JobConf job) {
    try {
      if (inputCategories == null){
        Set<String> newCategories = new HashSet<String>();

        DefaultStringifier<Set<String>> setStringifier =
            new DefaultStringifier<Set<String>>(job,GenericsUtil.getClass(newCategories));

        String categoriesStr = setStringifier.toString(newCategories);
        categoriesStr = job.get("wikipedia.categories", categoriesStr);
        
        inputCategories = setStringifier.fromString(categoriesStr);


      }
      exactMatchOnly = job.getBoolean("exact.match.only", false);
    } catch(IOException ex){
      throw new RuntimeException(ex);
    }
  }
}
