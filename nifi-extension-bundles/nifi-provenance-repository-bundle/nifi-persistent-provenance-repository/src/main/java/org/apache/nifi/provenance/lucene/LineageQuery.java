/*
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
package org.apache.nifi.provenance.lucene;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.nifi.provenance.ProvenanceEventRecord;
import org.apache.nifi.provenance.SearchableFields;
import org.apache.nifi.provenance.index.EventIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class LineageQuery {

    public static final int MAX_QUERY_RESULTS = 5000;
    public static final int MAX_LINEAGE_UUIDS = 100;
    private static final Logger logger = LoggerFactory.getLogger(LineageQuery.class);

    public static Set<ProvenanceEventRecord> computeLineageForFlowFiles(final IndexManager indexManager, final File indexDirectory,
        final String lineageIdentifier, final Collection<String> flowFileUuids, final DocumentToEventConverter docsToEventConverter) throws IOException {
        if (requireNonNull(flowFileUuids).size() > MAX_LINEAGE_UUIDS) {
            throw new IllegalArgumentException(String.format("Cannot compute lineage for more than %s FlowFiles. This lineage contains %s.", MAX_LINEAGE_UUIDS, flowFileUuids.size()));
        }

        if (lineageIdentifier == null && (flowFileUuids == null || flowFileUuids.isEmpty())) {
            throw new IllegalArgumentException("Must specify either Lineage Identifier or FlowFile UUIDs to compute lineage");
        }

        final EventIndexSearcher searcher;
        try {
            searcher = indexManager.borrowIndexSearcher(indexDirectory);
            try {
                // Create a query for all Events related to the FlowFiles of interest. We do this by adding all ID's as
                // "SHOULD" clauses and then setting the minimum required to 1.
                final BooleanQuery flowFileIdQuery;
                if (flowFileUuids == null || flowFileUuids.isEmpty()) {
                    flowFileIdQuery = null;
                } else {
                    final BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
                    for (final String flowFileUuid : flowFileUuids) {
                        final TermQuery termQuery = new TermQuery(new Term(SearchableFields.FlowFileUUID.getSearchableFieldName(), flowFileUuid));
                        queryBuilder.add(new BooleanClause(termQuery, BooleanClause.Occur.SHOULD));
                    }

                    flowFileIdQuery = queryBuilder.build();
                }

                final long searchStart = System.nanoTime();
                logger.debug("Searching {} for {}", indexDirectory, flowFileIdQuery);
                final TopDocs uuidQueryTopDocs = searcher.getIndexSearcher().search(flowFileIdQuery, MAX_QUERY_RESULTS);
                final long searchEnd = System.nanoTime();

                final Set<ProvenanceEventRecord> recs = docsToEventConverter.convert(uuidQueryTopDocs, searcher.getIndexSearcher().getIndexReader());

                final long readDocsEnd = System.nanoTime();
                logger.debug("Finished Lineage Query against {}; Lucene search took {} millis, reading records took {} millis",
                        indexDirectory, TimeUnit.NANOSECONDS.toMillis(searchEnd - searchStart), TimeUnit.NANOSECONDS.toMillis(readDocsEnd - searchEnd));

                return recs;
            } finally {
                indexManager.returnIndexSearcher(searcher);
            }
        } catch (final FileNotFoundException fnfe) {
            // nothing has been indexed yet, or the data has already aged off
            logger.warn("Attempted to search Provenance Index {} but could not find the file due to {}", indexDirectory, fnfe);
            if (logger.isDebugEnabled()) {
                logger.warn("", fnfe);
            }

            return Collections.emptySet();
        }
    }

}
