# Marés de Natal — projeto Android inicial

Aplicativo Android nativo em Kotlin com widget de tela inicial, painel escuro, toque para atualizar, cache local e atualização periódica via WorkManager (6 horas).

## Importante antes de usar previsões reais
- A referência institucional é a Tábua das Marés do Centro de Hidrografia da Marinha (CHM), que publica as previsões oficiais por porto e ano.
- A primeira implementação integra a API `https://tabuamare.api.br/api/v2`. A documentação pública informa os endpoints `/harbor_names/{state}` e `/tabua-mare/{harbor}/{month}/{days}`.
- **A identificação do código do Porto de Natal precisa ser validada na API antes de considerar esta versão pronta para uso.** A listagem documentada mostra um campo numérico `id`, enquanto o exemplo do endpoint de tábua usa códigos como `pb01`. O app não inventa dados: se a API não responder no formato esperado, manterá a última consulta e mostrará o status.
- Os horários e alturas não devem ser usados como orientação de segurança para navegação. Confira a fonte oficial, especialmente para atividades náuticas.

## Abrir e compilar
1. Instale o Android Studio atualizado.
2. Abra a pasta `MaresDeNatalAndroid` como projeto.
3. Aguarde a sincronização do Gradle.
4. Execute em um celular Android ou use **Build > Build APK(s)**.
5. Para adicionar o widget: pressione e segure a tela inicial > Widgets > Marés de Natal.

## O que esta versão já contém
- Widget redimensionável, aparência escura.
- Atualização manual pelo ícone ⟳.
- Consulta em segundo plano com WorkManager, intervalo de 6 horas.
- Cache da última resposta para exibição temporária offline.
- Horários e alturas formatados em metros.

## Próximos ajustes recomendados
- Confirmar o identificador do Porto de Natal na API e testar a resposta real.
- Refinar o layout compacto e expandido com RemoteViews responsivo.
- Adicionar rótulos explícitos “maré alta/baixa” pela sequência das alturas.
- Validar a mudança de mês/ano no período de dois dias.
- Comparar uma amostra de dias com a tábua oficial do CHM.


## Compilação automática pelo GitHub (sem computador)
O projeto inclui `.github/workflows/android-apk.yml`.
1. Crie um repositório no GitHub e envie o conteúdo desta pasta.
2. Abra a aba **Actions** do repositório.
3. Se solicitado, habilite os workflows.
4. Escolha **Gerar APK Android** e toque em **Run workflow** (ou faça o envio inicial para `main`, que inicia a compilação).
5. Quando terminar com status verde, abra a execução e baixe o artefato `MaresDeNatal-debug-apk`.
6. Extraia o ZIP baixado e instale `app-debug.apk` no Android.

A compilação na nuvem não significa que a previsão de marés já esteja validada. A API e o código do Porto de Natal ainda precisam ser testados contra a tábua oficial do CHM.
