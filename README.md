# Toolkit de Prospecção de Precatórios — TJ-SP

## Visão Geral

Conjunto de scripts para automatizar a prospecção de credores de precatórios no 
Tribunal de Justiça de São Paulo, conforme necessidade do escritório FUNPREC.

## Arquitetura das Fontes de Dados

```
┌─────────────────────────────────────────────────────────────┐
│                  3 Fontes de Dados Públicas                 │
├─────────────────┬──────────────────┬────────────────────────┤
│  1. e-SAJ       │  2. CAC/SCP      │  3. API DataJud (CNJ)  │
│  (Consulta      │  (Precatórios    │  (Metadados            │
│   Processual)   │   e Pagamentos)  │   processuais)         │
├─────────────────┼──────────────────┼────────────────────────┤
│ ✅ Público       │ ✅ Público        │ ✅ Chave pública       │
│ Busca por nome, │ Busca por nome,  │ API REST/Elasticsearch │
│ CPF, nº proc.   │ CPF, nº proc.    │ Até 10k registros/pag  │
│                 │                  │                        │
│ RETORNA:        │ RETORNA:         │ RETORNA:               │
│ • Partes/Cred.  │ • Nº precatório  │ • Classe, assunto      │
│ • Incidentes    │ • Valor          │ • Movimentações        │
│ • Andamentos    │ • Status pgto    │ • Órgão julgador       │
│ • Classe/Foro   │ • Posição fila   │ • Data ajuizamento     │
│                 │ • Ent. devedora  │                        │
└─────────────────┴──────────────────┴────────────────────────┘
```

## Fluxo de Prospecção Automatizada

```
Processo-semente (cliente entrou em contato)
    │
    ├── 1. Buscar no e-SAJ (Script 02)
    │       → Validar: processo existe? Ativo? Tem precatório?
    │       → Extrair PARTES (outros credores do mesmo processo)
    │       → Listar INCIDENTES de precatório vinculados
    │
    ├── 2. Para cada credor encontrado:
    │       → Consultar precatórios no CAC/SCP (Script 03)
    │       → Obter valor, status, posição na fila
    │
    ├── 3. Enriquecer com DataJud (Script 01)
    │       → Metadados adicionais, movimentações
    │
    ├── 4. Scoring automático
    │       → Valor acima do mínimo?
    │       → Ente devedor = SP ou Campinas?
    │       → Precatório pendente (não pago)?
    │       → Posição cronológica favorável?
    │
    └── 5. RESULTADO: Lista de leads qualificados
            → Nome, processo, valor estimado, score
            → Pronto para contato (WhatsApp Business API)
            → Loop: cada novo processo descoberto vira nova semente
```

## Scripts

### 01_datajud_api.py — API DataJud (CNJ)
```bash
# Instalar dependências
pip install requests pandas

# Buscar um processo específico
python 01_datajud_api.py --numero "1234567-89.2020.8.26.0053"

# Buscar precatórios de Campinas
python 01_datajud_api.py --classe-precatorio --municipio 3509502 --max 50

# Buscar precatórios de São Paulo capital
python 01_datajud_api.py --classe-precatorio --municipio 3550308 --max 50
```

### 02_esaj_scraper.py — Scraper e-SAJ (Consulta Pública)
```bash
# Instalar dependências
pip install requests beautifulsoup4 lxml pandas

# Buscar processos por nome
python 02_esaj_scraper.py buscar --nome "João da Silva"

# Buscar por CPF
python 02_esaj_scraper.py buscar --cpf "123.456.789-00"

# Buscar por número do processo
python 02_esaj_scraper.py buscar --numero "1234567-89.2020.8.26.0053"

# ⭐ PROSPECÇÃO RECURSIVA (a função principal)
python 02_esaj_scraper.py prospectar "1234567-89.2020.8.26.0053" \
    --profundidade 2 \
    --max-credores 50 \
    --output credores.csv
```

### 03_precatorio_portal.py — Portal de Precatórios TJ-SP
```bash
# Instalar dependências
pip install playwright pandas
playwright install chromium

# Mapear estrutura da página (para desenvolvimento)
python 03_precatorio_portal.py mapear "https://www.tjsp.jus.br/cac/scp/webmenupesquisa.aspx" --show

# Pesquisar precatórios por nome
python 03_precatorio_portal.py pesquisar --nome "João da Silva" --show
```

## Códigos IBGE dos Municípios (para a API DataJud)

| Município   | Código IBGE |
|-------------|-------------|
| São Paulo   | 3550308     |
| Campinas    | 3509502     |
| Guarulhos   | 3518800     |
| Santos      | 3548500     |
| Ribeirão P. | 3543402     |

## Análise de Viabilidade Técnica

### O que funciona SEM login de advogado
- ✅ Buscar processos por nome/CPF/número no e-SAJ
- ✅ Ver partes do processo (credores e réus)
- ✅ Ver incidentes vinculados (precatórios)
- ✅ Ver andamentos processuais
- ✅ Consultar precatórios no portal CAC/SCP
- ✅ Consultar metadados via API DataJud

### O que PRECISA de login (credenciais de advogado)
- 🔒 Acessar autos digitais (PDFs completos)
- 🔒 Ver contas de liquidação detalhadas
- 🔒 Peticionar ou movimentar processos

### Riscos e Mitigações
- **Rate limiting**: O TJ-SP pode bloquear IPs com muitas requisições
  → Mitigação: delay de 2s entre requests, rodar fora do horário
- **Mudanças no HTML**: A estrutura do e-SAJ pode mudar
  → Mitigação: seletores genéricos, testes automatizados
- **CAPTCHA**: Pode aparecer em buscas intensivas
  → Mitigação: usar browser automation (Playwright), resolver manualmente
- **Questões éticas/legais**: Prospecção ativa de credores
  → Consultar OAB sobre regras de publicidade advocatícia

## Próximos Passos Recomendados

1. **AGORA**: Rodar o Script 01 (DataJud) — mais fácil e confiável
2. **DEPOIS**: Testar o Script 02 com um processo real que a advogada forneça
3. **VALIDAR**: Confirmar com a advogada se os dados extraídos são suficientes
4. **ITERAR**: Ajustar seletores CSS conforme a estrutura real das páginas
5. **PRODUÇÃO**: Integrar com banco de dados e dashboard
