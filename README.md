# Spring AI GenAI Platform

A production-oriented GenAI application built with Spring AI.

This repository is being developed progressively through multiple stages, where each stage extends the previous version into a more capable and production-ready AI system.

## Current Stage

### Project 1 — AI Copilot

Project 1 establishes the foundation of the platform with:

- Spring Boot + Spring AI
- Groq as the AI provider
- `openai/gpt-oss-120b` model
- Chat API
- Conversation memory
- Structured output
- Basic AI guardrails
- Retry handling
- Timeout handling
- Circuit Breaker
- Rate Limiter
- Bulkhead
- Global exception handling
- Request validation
- AI request/error/latency metrics
- Actuator health and metrics
- Structured application logs
- Unit tests
- Integration tests

## Architecture

```text
Client
   |
   v
ChatController
   |
   v
ChatService
   |
   v
AiProviderClient
   |
   v
Spring AI ChatClient
   |
   v
Groq
   |
   v
GPT OSS 120B
