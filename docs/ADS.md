# Anuncios

## Qual rede usar

O requisito e: **o app nao precisa estar na Play Store (nem em loja nenhuma) e a
rede nao exige aprovacao**. Isso elimina o AdMob como opcao principal — o AdMob so
serve em producao para apps listados no Google Play. As redes que atendem ao
requisito:

| Rede | Como entra | Aprovacao | Loja |
| --- | --- | --- | --- |
| **Adsterra** | Self-Serve Publisher -> "Smartlink" / "Direct Link" | minutos, sem revisao manual | nao precisa |
| **Monetag** | Self-Serve -> canal de distribuicao **SDK** (APK) | self-serve | nao precisa |
| **PropellerAds** | Self-Serve -> "Direct Link" | self-serve | nao precisa |

O formato "Direct Link" e uma URL de zona: nao precisa de SDK, nao precisa de
banner, e funciona dentro do APK. E o provider padrao do TurboTouch.

Observacoes reais de cada uma:

* Adsterra aprova publisher em ~5-10 min e nao exige volume minimo de trafego.
* Monetag exige 1000 DAU para monetizacao de app (limite da plataforma, nao do SDK).
* Ambas pagam por impressao/visita no link direto — o clique e do usuario.

## Credenciais

1. Crie a conta de publisher na rede escolhida.
2. Crie uma zona do formato **Direct Link / Smartlink** e copie a URL.
3. Cole no arquivo `ads_config.properties` na raiz do repositorio:

```properties
ads.enabled=true
ads.provider=smartlink
ads.smartlink.url=https://smart.adsterra.com/smartlink/SEU_ZONE_ID
```

4. Reconstrua o app (`./gradlew :app:assembleDebug`). O build embala o arquivo em
   `assets/ads_config.properties`.

Para mudar sem recompilar, a tela **Ads** do app salva um override em
`filesDir/ads_config_override.properties`, que tem precedencia sobre o asset.

## Todas as chaves

| Chave | O que faz |
| --- | --- |
| `ads.enabled` | liga/desliga o modulo inteiro |
| `ads.provider` | `smartlink` \| `admob` \| `reflective` \| `none` |
| `ads.smartlink.url` | URL da zona de Direct Link |
| `ads.admob.app_id` | app id do AdMob (vai para o manifest no build) |
| `ads.admob.banner_unit` | id do bloco de banner |
| `ads.admob.interstitial_unit` | id do bloco de interstitial |
| `ads.background.interval_sec` | intervalo minimo entre exibicoes do slot de fundo (0 = desliga) |
| `ads.debug` | logs verbosos + ids de teste do Google no emulador |
| `ads.reflective.init_class` | classe do SDK que voce quer ligar por reflexao |
| `ads.reflective.init_method` | metodo de inicializacao (padrao `initialize`) |
| `ads.reflective.banner_class` | classe do banner do SDK |
| `ads.reflective.interstitial_class` | classe do interstitial do SDK |

Precedencia de leitura do arquivo: `ads_config.properties` (versionado) +
`ads_config.local.properties` (ignorado pelo git, tem precedencia) + override em
tempo de execucao.

## Slots

* **Slot de fundo** — quando o app vai para segundo plano, o TurboTouch marca um
  slot pendente; quando o usuario volta, a landing do Direct Link abre dentro do
  app (mesmo modelo do popunder). O intervalo minimo e `ads.background.interval_sec`.
* **Banner** — barra discreta na tela **Ads** (a tela serve tambem de preview).
* **Interstitial** — botao "Mostrar agora", Provider `admob`.

## Testar clique (QA)

A tela **Ads** tem um botao **Testar clique (QA)**. Ele dispara um toque sintetico
no centro do seu proprio placement de anuncio e informa se o evento chegou no
SDK — e o jeito de conferir, nos ids de teste do anunciante, se:

* o click esta sendo registrado;
* o destino (CTA) abre;
* o overlay do banner esta interceptando o toque.

O harness trabalha dentro do seu app e nos seus ids de teste. Clique em trafego
real, sem usuario, e trafego invalido: a rede bloqueia a conta e o anunciante
perde dinheiro. O que o modulo entrega e o slot real (impressao/visita com o
usuario presente), nao clique fabricate.

## Consentimento

Se a rede oferecer consentimento explicito (Monetag/Adsterra tem `setUserConsent`
no SDK), chame **antes** de inicializar o provider. O TurboTouch nao coleta dado
alheio ao app: os SDKs de anuncio sao os unicos que enviam dados.
