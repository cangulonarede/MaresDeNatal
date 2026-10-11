# DeuPraia!

Projeto Android nativo para widget de tábua de marés de Natal/RN.

## Alterações desta versão
- Nome do app/widget: DeuPraia!
- Widget em duas colunas: hoje e amanhã.
- Mostra horários e marcadores de maré alta/baixa, sem exibir alturas.
- Botão manual de atualização e atualização em segundo plano a cada seis horas.
- Workflow GitHub Actions para gerar APK de teste.

## Gerar APK
O GitHub Actions gera o artefato `DeuPraia-debug-apk`. Baixe e extraia para localizar `app-debug.apk`.

## Observação importante
A classificação alta/baixa depende da sequência de alturas retornada pela API e precisa ser validada com a tábua oficial do Centro de Hidrografia da Marinha. Este projeto não substitui a fonte oficial para atividades náuticas.
