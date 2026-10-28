# Providers and compatible services

Which services this library can reach, how, and how sure we are of each. The
[reference](part-2-reference.md#providers) describes the provider modules and the keys; this
page answers a different question: *"I use Kimi, or DeepSeek, or Groq — does it work?"*

A service that has no module of its own is often still reachable. Many vendors accept the
OpenAI protocol, and some the Anthropic one, so `provider = openai` or `provider = anthropic`
with a `base-url` reaches them. [The `base-url` section](part-2-reference.md#local-models-and-openai-compatible-servers-base-url)
of the reference explains how that works.

## How sure each row is

Every row says one of three things:

| Level | What it means | Who stands behind it |
|---|---|---|
| **Module** | A `modelrack4j-provider-*` module of its own, with an integration test against the real service | This project, at every release |
| **Tested** | Through `openai` or `anthropic` with a `base-url`. One chat call and one streaming call were made by hand against the real service, on the date shown | This project, on that date |
| **Declared** | Through `openai` or `anthropic` with a `base-url`. The vendor says it is compatible; nobody here has run it | The vendor |

A date is the day the row was last checked: for **Module**, the last time the integration
test ran; for **Tested**, the day of the two calls; for **Declared**, the day the address and
the model name were read from the vendor's own documentation. A row keeps its date until
someone checks it again, so an old date means exactly that. Model names change often: check
yours against the vendor's list.

## The services

| Service | `provider =` | `base-url` | `api-key` | Level | Date | Known limits |
|---|---|---|---|---|---|---|
| OpenAI | `openai` | leave it out | required | Module | 2026-10-27 | |
| Anthropic | `anthropic` | leave it out | required | Module | 2026-10-27 | no moderation; token counting is a billed call |
| Google Gemini | `gemini` | leave it out | required | Module | 2026-10-27 | no moderation; token counting is a billed call |
| Zhipu GLM | `glm` | leave it out | required, `id.secret` | Module | 2026-10-27 | no moderation, no token counting |
| Mistral | `mistral` | leave it out | required | Module | 2026-10-27 | no token counting |
| Ollama | `ollama` | `http://localhost:11434` | not allowed | Module | 2026-10-27 | no moderation, no token counting |
| Ollama, through its OpenAI endpoint | `openai` | `http://localhost:11434/v1` | leave it out | Tested | 2026-10-26 | [both limits below](#limits-of-a-row-that-is-not-a-module); prefer `provider = ollama` |
| LocalAI, llama.cpp's server, vLLM, LM Studio | `openai` | the server's OpenAI-compatible address | only if the server checks one | Declared | — | [both limits below](#limits-of-a-row-that-is-not-a-module) |
| Kimi (Moonshot AI) | `openai` | `https://api.moonshot.ai/v1` | required | Tested | 2026-10-28 | [both limits below](#limits-of-a-row-that-is-not-a-module) |
| Kimi (Moonshot AI), Anthropic protocol | `anthropic` | `https://api.moonshot.ai/anthropic/v1` | required | Tested | 2026-10-28 | see [Kimi through `anthropic`](#kimi-through-anthropic) |
| DeepSeek | `openai` | `https://api.deepseek.com` | required | Declared | 2026-10-26 | [both limits below](#limits-of-a-row-that-is-not-a-module) |
| Groq | `openai` | `https://api.groq.com/openai/v1` | required | Declared | 2026-10-26 | [both limits below](#limits-of-a-row-that-is-not-a-module) |
| OpenRouter | `openai` | `https://openrouter.ai/api/v1` | required | Declared | 2026-10-26 | [both limits below](#limits-of-a-row-that-is-not-a-module) |
| Together AI | `openai` | `https://api.together.ai/v1` | required | Declared | 2026-10-26 | no moderation endpoint, the vendor says; [both limits below](#limits-of-a-row-that-is-not-a-module) |
| xAI | `openai` | `https://api.x.ai/v1` | required | Declared | 2026-10-26 | [both limits below](#limits-of-a-row-that-is-not-a-module) |

The Ollama row through `openai` was tested against Ollama 0.17.5 with the `tinyllama` model,
and both Kimi rows with `kimi-k3.1-pro`.
The LocalAI row has no date: those servers run on your machine, so there is no single address
to read, and their documentation was not read again for this page. Give the address your
server prints when it starts, which usually ends in `/v1`.

## Limits of a row that is not a module

A block through `openai` or `anthropic` gets that provider's capabilities, not the
service's. Two of them matter:

- **Moderation.** The `openai` provider reports that it can moderate, and core believes it,
  because the provider cannot know what the server behind a `base-url` supports. So a block
  with `moderation { enabled = true }` loads, and its first moderation call fails if the
  service has no `/moderations` endpoint. Ollama has none: the call fails with
  `ModelNotFoundException` and HTTP 404. Leave moderation off unless the vendor documents the
  endpoint.
- **`token-window` memory.** The `openai` provider counts tokens with OpenAI's own tokenizer,
  which knows only OpenAI model names. A block with a `model-name` the tokenizer does not know
  and `memory.type = token-window` is refused when it loads, with a message that says so. Use
  `message-window`.

Through `anthropic` there is no moderation at all. Token counting there is a billed call, and
with a `base-url` it goes to the service, which then has to answer Anthropic's counting
endpoint as well; leave `token-window` off unless the vendor documents that endpoint.

### Kimi through `anthropic`

Kimi documents an endpoint that speaks Anthropic's Messages API. Its page gives
`https://api.moonshot.ai/anthropic` as the base address and `/anthropic/v1/messages` as the
path. The `anthropic` provider adds only `messages` to the address it is given, so the
`base-url` here ends in `/v1`. Kimi's documentation gives its key in an `Authorization: Bearer`
header, and LangChain4j's Anthropic client sends an `x-api-key` header instead; Kimi accepts it,
which the documentation does not say. Both calls through this route answered.

A new Kimi account starts with a low rate limit — three requests a minute on the account used
here — so the fourth of four quick calls was refused with `rate_limit_reached_error` and
answered when repeated half a minute later. The limit grows with the amount the account has
paid in, and it applies to the whole account, whichever route a block uses.

## Not covered

**Azure OpenAI, Amazon Bedrock, Google Vertex AI and IBM watsonx have no row.** LangChain4j
has a module for each, but they are usually set up with more than one key and one address — a
cloud identity, a region, a project or a deployment name — and this library takes one
`api-key` and one `base-url` per block. Whether any of them fits that shape has not been
checked, so this project makes no claim either way, through a module of its own or through
`provider = openai`
([ADR-0062](../adr/0062-a-provider-declares-its-key-requirements.md) leaves them out for that
reason).

## The examples

Every row has a block in
[`catalog.conf`](../../modelrack4j-examples/src/main/resources/catalog.conf), ready to copy.
A test in `modelrack4j-examples` loads that file, and every `hocon` block on this page, with
placeholder keys, and checks that each one passes validation. It sends no request, so it
proves the blocks are well formed, not that the services answer. That is what the levels are
for.

```hocon
llm {
  OPENAI    { provider = openai,    api-key = ${OPENAI_API_KEY},    model-name = "gpt-5-mini" }
  ANTHROPIC { provider = anthropic, api-key = ${ANTHROPIC_API_KEY}, model-name = "claude-sonnet-4-6" }
  GEMINI    { provider = gemini,    api-key = ${GEMINI_API_KEY},    model-name = "gemini-3.6-flash" }
  GLM       { provider = glm,       api-key = ${ZHIPU_API_KEY},     model-name = "glm-5.3" }
  MISTRAL   { provider = mistral,   api-key = ${MISTRAL_API_KEY},   model-name = "mistral-small-latest" }
  OLLAMA    { provider = ollama, base-url = "http://localhost:11434", model-name = "llama3.2" }
}
```

```hocon
llm {
  OLLAMA-OPENAI { provider = openai, base-url = "http://localhost:11434/v1", model-name = "llama3.2" }
  LOCAL-SERVER  { provider = openai, base-url = "http://localhost:8080/v1",  model-name = "your-model" }
}
```

```hocon
llm {
  KIMI {
    provider   = openai
    base-url   = "https://api.moonshot.ai/v1"
    api-key    = ${MOONSHOT_API_KEY}
    model-name = "kimi-k3.1-pro"
  }
  KIMI-ANTHROPIC {
    provider   = anthropic
    base-url   = "https://api.moonshot.ai/anthropic/v1"
    api-key    = ${MOONSHOT_API_KEY}
    model-name = "kimi-k3.1-pro"
  }
  DEEPSEEK {
    provider   = openai
    base-url   = "https://api.deepseek.com"
    api-key    = ${DEEPSEEK_API_KEY}
    model-name = "deepseek-flash"
  }
  GROQ {
    provider   = openai
    base-url   = "https://api.groq.com/openai/v1"
    api-key    = ${GROQ_API_KEY}
    model-name = "llama-3.3-70b-versatile"
  }
  OPENROUTER {
    provider   = openai
    base-url   = "https://openrouter.ai/api/v1"
    api-key    = ${OPENROUTER_API_KEY}
    model-name = "~openai/gpt-sol-latest"
  }
  TOGETHER {
    provider   = openai
    base-url   = "https://api.together.ai/v1"
    api-key    = ${TOGETHER_API_KEY}
    model-name = "MiniMaxAI/MiniMax-M3"
  }
  XAI {
    provider   = openai
    base-url   = "https://api.x.ai/v1"
    api-key    = ${XAI_API_KEY}
    model-name = "grok-4.7"
  }
}
```

The `LOCAL-SERVER` address is an example: use the one your server prints. Each key is read
with `${VAR}`, so a missing variable fails when the file loads and names the variable. The
variable names are this page's choice, not the vendors'.

## Where the values come from

Read from each vendor's documentation on 2026-10-26:
[Kimi](https://platform.kimi.ai/docs/guide/start-using-kimi-api) and its
[Messages API](https://platform.kimi.ai/docs/api/messages),
[DeepSeek](https://api-docs.deepseek.com/),
[Groq](https://console.groq.com/docs/openai) and its [models](https://console.groq.com/docs/models),
[OpenRouter](https://openrouter.ai/docs/quickstart),
[Together AI](https://docs.together.ai/docs/openai-api-compatibility),
[xAI](https://docs.x.ai/docs/overview).

To add a service, read the same three things from its own documentation — the address, a
model name, which protocol it speaks — add a row with the date, a block in `catalog.conf` and
the same block here, and mark it **Declared**. It becomes **Tested** only when someone has made
the two calls.
