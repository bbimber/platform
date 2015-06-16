/*
 * Copyright (c) 2010-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.search.model;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;


/**
 * User: adam
 * Date: Apr 19, 2010
 * Time: 9:02:20 PM
 */
@SuppressWarnings({"UnusedDeclaration"})
public enum ExternalAnalyzer
{
    SimpleAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new SimpleAnalyzer();
        }},
    KeywordAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new KeywordAnalyzer();
        }},
    EnglishAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new EnglishAnalyzer();
        }};

    abstract Analyzer getAnalyzer();
}
