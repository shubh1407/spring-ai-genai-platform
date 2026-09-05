# Project 2 — RAG

This project extends the AI Copilot with a Retrieval-Augmented Generation (RAG) knowledge layer.

---

# Day 1 — RAG Fundamentals + Architecture

## What is RAG?

Retrieval-Augmented Generation (RAG) is an architecture where relevant external knowledge is retrieved and supplied to an LLM before it generates an answer.

Instead of relying only on the model's training knowledge:

```text
User Question
      ↓
Retrieve relevant knowledge
      ↓
Build context
      ↓
LLM
      ↓
Answer
```

The two fundamental responsibilities are:

```text
Retrieval  → Find relevant knowledge
Generation → Generate the answer
```

RAG connects these two stages.

## Why RAG?

An LLM may not know:

- private company information
- internal policies
- application-specific knowledge
- information stored in uploaded documents
- information that changes independently of the model

RAG allows application knowledge to live outside the model and be retrieved at request time.

## RAG Architecture

### Ingestion flow

```text
Source Files
    ↓
Documents
    ↓
Transformation
    ↓
Chunks
    ↓
Embeddings
    ↓
Vector Store
```

### Query flow

```text
Question
    ↓
Query Embedding
    ↓
Similarity Search
    ↓
Relevant Chunks
    ↓
Context
    ↓
LLM
    ↓
Answer
```

## Responsibility separation

### Document ingestion

Converts external sources into application-level Documents.

### Chunking

Divides documents into retrieval-friendly pieces.

### Embedding

Converts text into numerical vector representations.

### Vector Store

Stores vectors together with document information and supports similarity search.

### Retrieval

Finds the information relevant to a query.

### LLM

Generates the answer using the supplied information.

## Core mental model

```text
Knowledge side:

Files
 ↓
Documents
 ↓
Chunks
 ↓
Embeddings
 ↓
Vector Store


Question side:

Question
 ↓
Query Embedding
 ↓
Vector Search
 ↓
Relevant Knowledge
```

RAG is therefore not simply "asking an LLM a question." It is a pipeline that connects external knowledge retrieval with generation.

---

# Day 2 — Document Ingestion / Loaders

## Spring AI Document

Spring AI represents source content using:

```text
org.springframework.ai.document.Document
```

A Document conceptually contains:

```text
Document
├── ID
├── Text / Content
└── Metadata
```

The Document is the application-level representation used by later RAG stages.

## Document ingestion

The purpose of ingestion is:

```text
Source file
    ↓
Resource
    ↓
Appropriate reader
    ↓
List<Document>
```

The project currently handles:

```text
.txt
.json
.pdf
```

## Resource discovery

The application discovers resources from the knowledge directory.

Conceptually:

```text
classpath:/knowledge/*
```

The wildcard is important because the application needs to resolve the files inside the directory rather than treating the directory itself as the document.

## Reader selection

The file extension determines which reader is used:

```text
txt
 ↓
TextReader

json
 ↓
JsonReader

pdf
 ↓
PagePdfDocumentReader
```

Unsupported file formats are rejected through the application's validation/error handling.

## TXT

A text file can be converted into a Document while preserving useful metadata.

Example metadata observed:

```text
charset=UTF-8
source=company-policy.txt
filename=company-policy.txt
fileType=txt
```

## JSON

JSON content is converted into a Spring AI Document.

The application enriches the resulting metadata where required, for example:

```text
filename=company.json
fileType=json
```

## PDF

`PagePdfDocumentReader` produces page-oriented Documents.

Example metadata observed:

```text
page_number=1
filename=company.pdf
file_name=company.pdf
fileType=pdf
```

## Page vs RAG chunk

A PDF page is not automatically a RAG chunk.

The reader may produce:

```text
PDF
 ↓
Page 1
Page 2
Page 3
```

Later, the chunking stage can transform those Documents into smaller retrieval-oriented chunks.

Therefore:

```text
PDF page ≠ RAG chunk
```

A page is a source/document boundary.

A chunk is a retrieval boundary.

## Metadata

Metadata is information associated with a Document that describes the content or its origin.

Examples:

```text
filename
fileType
source
charset
page_number
file_name
```

Metadata is important because later RAG stages can use it for:

- filtering
- identifying the source
- associating chunks with documents
- retrieval constraints
- future authorization/multi-tenancy requirements

---

# Day 3 — Document Transformation + Chunking

## Why chunk documents?

A large document is often too coarse as a single retrieval unit.

For example:

```text
Large Document
 ├── Refund Policy
 ├── Leave Policy
 ├── Security Policy
 └── Contact Information
```

If the complete document is embedded as one unit, a query about one topic may retrieve the whole document.

Chunking creates smaller retrieval units:

```text
Large Document
      ↓
Chunk 1
Chunk 2
Chunk 3
Chunk 4
```

The goal is not merely to split text.

The goal is to create **retrieval-friendly semantic units**.

## TextSplitter

Spring AI provides the `TextSplitter` abstraction for document splitting.

The application injects a `TextSplitter` bean into the chunking service.

Conceptually:

```text
Documents
    ↓
TextSplitter
    ↓
Chunks
```

## TokenTextSplitter

The project uses `TokenTextSplitter`.

Current configuration:

```java
TokenTextSplitter.builder()
        .withChunkSize(20)
        .withMinChunkSizeChars(10)
        .withMinChunkLengthToEmbed(2)
        .withMaxNumChunks(10000)
        .build();
```

## `withChunkSize(20)`

Controls the approximate token-oriented chunk size.

Smaller chunks generally provide:

- finer retrieval granularity
- potentially better precision for focused questions
- more chunks
- more embedding operations
- more storage/retrieval overhead

Larger chunks generally provide:

- more surrounding context
- fewer chunks
- potentially less precise retrieval
- larger retrieved context

Chunk size is therefore a quality/cost/context trade-off.

## `withMinChunkSizeChars(10)`

Defines a minimum character-oriented chunk size.

It helps prevent the creation of extremely small fragments.

## `withMinChunkLengthToEmbed(2)`

Defines the minimum chunk length considered eligible for embedding.

Extremely small pieces usually provide little useful semantic information.

## `withMaxNumChunks(10000)`

Places an upper bound on the number of chunks produced.

This helps protect against unexpectedly creating an excessive number of chunks.

## Chunk overlap

Chunk overlap is a common RAG technique for preserving context across chunk boundaries.

Example:

```text
Chunk 1:
A B C D E

Chunk 2:
        D E F G H
        ↑ overlap
```

Benefits:

- preserves boundary context
- reduces the chance that a concept split at a boundary loses meaning

Costs:

- duplicated content
- more chunks
- more embeddings
- more storage
- potentially more retrieval noise

### Version-specific implementation note

The Spring AI 2.0.0 `TokenTextSplitter` builder used in this project does not expose a `withOverlap(...)` builder method.

Therefore the project does not invent an overlap configuration that the actual version does not provide.

## Current chunking result

The current knowledge dataset produced:

```text
company-policy.txt → 4 chunks
company.json       → 1 chunk
company.pdf        → 6 chunks

Total              → 11 chunks
```

Chunk metadata includes information such as:

```text
chunk_index
total_chunks
parent_document_id
```

This demonstrates the transformation:

```text
Documents
    ↓
Chunks
```

---

# Day 4 — Embeddings + EmbeddingModel

## What is an embedding?

An embedding is a numerical vector representation of text.

Example:

```text
"Company refund policy"
          ↓
    Embedding Model
          ↓
[0.12, -0.43, 0.81, ...]
```

The vector is not intended to be human-readable.

Its purpose is to represent text in a mathematical space where related meanings can be compared.

## Why embeddings are needed in RAG

Keyword search looks for matching words.

Semantic retrieval instead tries to find content with similar meaning.

For example:

```text
Query:
"Can I get my money back?"

Document:
"Customers can request a refund within 30 days."
```

The wording is different, but the meanings are related.

Embeddings allow the application to represent both pieces of text in a semantic vector space.

## EmbeddingModel

Spring AI provides the `EmbeddingModel` abstraction.

Conceptually:

```text
Text
 ↓
EmbeddingModel
 ↓
Vector
```

The application depends on the abstraction rather than scattering provider-specific embedding calls throughout the code.

## Embedding request/response

Conceptually:

```text
EmbeddingRequest
├── Inputs
│   ├── text 1
│   ├── text 2
│   └── text 3
└── EmbeddingOptions

EmbeddingResponse
├── Embedding results
└── Response metadata
```

This abstraction becomes useful when embedding multiple texts and when provider/model-specific options need to be supplied.

## Why `EmbeddingOptions` exists

Embedding is not always simply:

```text
text → vector
```

An embedding provider/model can expose configuration such as:

- model
- task type
- output dimensions
- truncation behavior
- provider-specific options

`EmbeddingOptions` provides a way to carry such embedding configuration with the request.

## Google GenAI

The project currently uses Google GenAI for text embeddings.

Conceptually:

```text
Application
    ↓
Spring AI EmbeddingModel
    ↓
Google GenAI
    ↓
Embedding Model
    ↓
Vector
```

## Embedding task types

The Google GenAI embedding API supports task-oriented configurations such as:

```text
RETRIEVAL_QUERY
RETRIEVAL_DOCUMENT
SEMANTIC_SIMILARITY
CLASSIFICATION
CLUSTERING
QUESTION_ANSWERING
FACT_VERIFICATION
```

For RAG, the important conceptual distinction is:

```text
Document → document-oriented representation
Query    → query-oriented representation
```

The exact available task types depend on the selected provider/model.

## Same embedding model for documents and queries

Document vectors and query vectors must be compatible.

Correct conceptual flow:

```text
Documents
   ↓
Embedding Model A
   ↓
Document Vectors


Query
   ↓
Embedding Model A
   ↓
Query Vector
```

Then the query vector can be compared against the document vectors.

Changing the embedding model can change the vector space.

Therefore changing embedding models can require re-indexing existing documents.

## Vector dimensions

An embedding has a fixed dimensionality for a given model/configuration.

Example:

```text
Vector:
[x1, x2, x3, ... xn]

Dimensions = n
```

The vector store must be compatible with that dimensionality.

Conceptually:

```text
Embedding Model
      ↓
Dimension N
      ↓
Vector Store
      ↓
Stored vectors must be compatible with N
```

Changing the model or output dimension can therefore become an indexing/re-indexing concern.

---

# Day 5 — Vector Store

## What is a Vector Store?

A Vector Store provides the storage and retrieval abstraction for documents with their vector representations and metadata.

Conceptually:

```text
Document
├── Text
├── Metadata
└── Embedding Vector

        ↓

   Vector Store
```

It allows the application to store indexed knowledge and retrieve documents based on vector similarity.

## Vector Store vs Vector Database

### Vector Store

An application-level abstraction/interface for storing and retrieving vectorized documents.

### Vector Database

A concrete database technology designed to persist and search vector data efficiently.

Conceptually:

```text
Application
     ↓
VectorStore abstraction
     ↓
Concrete implementation
```

The concrete implementation can vary.

## SimpleVectorStore

The project currently uses Spring AI's `SimpleVectorStore`.

It is an in-memory VectorStore implementation.

Conceptually:

```text
Application
     ↓
SimpleVectorStore
     ↓
Application Memory
```

It is useful for:

- learning
- local development
- testing
- understanding vector retrieval mechanics

It is not the final production persistence solution.

## Indexing documents

The VectorStore can index Documents using:

```text
vectorStore.add(documents)
```

Conceptually:

```text
Chunks
   ↓
VectorStore.add()
   ↓
EmbeddingModel
   ↓
Vectors
   ↓
Stored with text + metadata
```

The VectorStore integration can use the configured `EmbeddingModel` to generate embeddings while indexing.

## Important implementation lesson — avoid double embedding

The application initially performed:

```text
1. Manually generate embeddings
2. Call vectorStore.add(chunks)
```

This can cause the same chunks to be embedded twice.

The normal indexing flow should be:

```text
Files
 ↓
Documents
 ↓
Chunks
 ↓
vectorStore.add(chunks)
 ↓
EmbeddingModel
 ↓
Stored vectors
```

The explicit `DocumentEmbeddingService` remains useful for learning/testing the `EmbeddingModel`, but it should not unnecessarily duplicate the indexing work.

## Similarity search

After indexing:

```text
User Query
     ↓
Query Embedding
     ↓
Vector Store
     ↓
Similarity Comparison
     ↓
Ranked Documents
```

The query is converted into a vector using the configured embedding model.

The VectorStore compares the query representation against stored vectors.

## `similaritySearch`

The VectorStore abstraction provides similarity search.

Conceptually:

```java
vectorStore.similaritySearch(query);
```

This asks the VectorStore for documents semantically similar to the query.

## SearchRequest

For controlled retrieval, Spring AI provides `SearchRequest`.

Conceptually:

```text
SearchRequest
├── query
├── topK
├── similarityThreshold
└── metadata filter
```

This allows retrieval behavior to be configured per search.

## `topK`

`topK` means:

> Maximum number of results to return.

Example:

```text
topK = 2
```

means the VectorStore should return at most two results.

It does not mean that two useful results necessarily exist.

## TopK trade-off

Smaller `topK`:

```text
↓ retrieved context
↓ latency
↓ token cost
potentially ↓ recall
```

Larger `topK`:

```text
↑ retrieved coverage
potentially ↑ recall
↑ context
↑ latency
↑ token cost
potentially ↓ precision
```

Therefore `topK` is a retrieval quality and system cost parameter.

## Similarity threshold

A similarity threshold defines the minimum relevance requirement for returned results.

Conceptually:

```text
Query
 ↓
Similarity scores
 ↓
Apply threshold
 ↓
Accepted results
```

If a result does not meet the threshold, it should not automatically be treated as relevant.

This becomes particularly important when the knowledge base may not contain an answer to the user's question.

## Threshold trade-off

Higher threshold:

```text
↑ precision
↓ recall
```

Lower threshold:

```text
↑ recall
↓ precision
```

There is no universal threshold.

The appropriate value depends on:

- embedding model
- dataset
- query distribution
- business requirements
- similarity metric
- desired precision/recall balance

## Metadata filtering

Similarity is not always the only retrieval constraint.

Metadata can restrict which documents are eligible.

Example:

```text
fileType = pdf
```

Conceptually:

```text
Query
  ↓
Metadata constraint
  ↓
Eligible documents
  ↓
Similarity search
```

This allows retrieval to combine:

```text
Semantic similarity
+
Metadata constraints
```

## Metadata filter example

The project tested filters using:

```text
fileType = pdf
```

and:

```text
fileType = txt
```

The returned results were restricted to the requested file type.

This demonstrates that retrieval can use both semantic similarity and structured metadata constraints.

## Metadata filtering and security

Metadata filtering can become important for:

- tenant isolation
- department-level access
- document categories
- source restrictions
- authorization boundaries

For example:

```text
tenantId = companyA
```

should not allow retrieval of:

```text
tenantId = companyB
```

The important architectural principle is:

> Authorization must be enforced by the application/retrieval layer, not trusted to the LLM.

The LLM should never be responsible for deciding whether a user is allowed to access retrieved data.

## Similarity score and distance

Vector stores may expose values representing similarity or distance.

The exact interpretation depends on the vector-store implementation and metric.

Therefore a numeric result should not automatically be assumed to have the same meaning across every VectorStore implementation.

## Common vector comparison metrics

### Cosine similarity

Measures directional similarity between vectors.

### Euclidean distance

Measures geometric distance between vectors.

### Inner product / dot product

Measures the product relationship between vector components and is commonly used in vector retrieval.

The exact metric is determined by the vector-store implementation/configuration.

## Vector dimension compatibility

Stored vectors and query vectors must belong to a compatible vector space.

Conceptually:

```text
Embedding Model
      ↓
Dimension N
      ↓
Stored vectors
      ↕
Query vector
```

If the application changes to an incompatible embedding model/dimension, existing vectors may need to be regenerated.

## Duplicate indexing behavior

If the same Documents/chunks are indexed repeatedly without lifecycle/idempotency control, duplicate entries can appear.

Example:

```text
Index company.pdf
      ↓
Index company.pdf again
      ↓
Duplicate chunks
```

Possible consequence:

```text
Query
 ↓
Similarity search
 ↓
Repeated/duplicate results
```

The important lesson is:

> Indexing should be treated as a controlled lifecycle operation rather than blindly repeated every time.

The complete update/deduplication/re-indexing implementation is intentionally handled in the later ingestion-lifecycle work of the project.

## SimpleVectorStore persistence limitation

`SimpleVectorStore` is in-memory.

Conceptually:

```text
Application starts
      ↓
Vector Store in memory
      ↓
Index documents
      ↓
Vectors available
      ↓
Application stops
      ↓
In-memory state is lost
```

Therefore it is not the final production persistence mechanism when vector data must survive application restarts.

## Current Project 2 flow through Day 5

```text
Files
  ↓
Document Ingestion
  ↓
Documents + Metadata
  ↓
Chunking
  ↓
Chunks
  ↓
VectorStore.add()
  ↓
EmbeddingModel
  ↓
Stored Vectors
  ↓
Similarity Search
  ↓
topK / Threshold / Metadata Filter
  ↓
Relevant Documents
```

---

# Day 5 — Key Concepts Revision

## RAG

Retrieves external knowledge and makes it available to the LLM before generation.

## Document

Spring AI's representation of text/content plus metadata and identity.

## Chunk

A smaller retrieval-oriented unit produced from a Document.

## Embedding

A numerical vector representation of text used for semantic comparison.

## EmbeddingModel

Spring AI abstraction responsible for generating embeddings.

## VectorStore

Abstraction responsible for storing and retrieving vectorized documents.

## SimpleVectorStore

In-memory VectorStore implementation useful for development/testing.

## Similarity search

Retrieves documents whose vector representations are similar to the query representation.

## TopK

Maximum number of search results returned.

## Similarity threshold

Minimum relevance/similarity requirement for accepted search results.

## Metadata filter

Structured constraint that limits eligible documents during retrieval.

## Vector dimension

The number of numerical values in an embedding vector; stored and query vectors must be compatible.

## Duplicate indexing

Repeated indexing of the same chunks can produce duplicate stored entries unless lifecycle/idempotency is controlled.

## Persistence limitation

An in-memory VectorStore does not provide durable application-restart persistence by itself.

---

# Day 5 — Definition of Done

- [x] Understand what a Vector Store is
- [x] Understand Vector Store vs Vector Database
- [x] Understand `SimpleVectorStore`
- [x] Add/index Documents
- [x] Understand similarity search
- [x] Use `SearchRequest`
- [x] Understand `topK`
- [x] Understand similarity threshold
- [x] Understand metadata filtering
- [x] Understand similarity/distance
- [x] Understand vector dimensions
- [x] Understand duplicate indexing behavior
- [x] Understand in-memory persistence limitation
- [x] Understand how indexing connects chunks, embeddings and VectorStore
- [x] Understand why manual embedding before `VectorStore.add()` can cause unnecessary duplicate embedding work
