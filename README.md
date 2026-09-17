# TrashWay

Aplicativo Android que ajuda a encontrar a **lixeira pública mais próxima** e a **reportar problemas** nas lixeiras da cidade.

Projeto desenvolvido pelos alunos do 8º semestre de Engenharia da Computação da **Universidade Santa Cecília (Santos/SP)**. A base de dados conta com quase 100 lixeiras mapeadas na orla e nos bairros de Santos (Gonzaga, Boqueirão, Av. Conselheiro Nébias etc.).

## Telas

| Abertura | Início | Procurar Lixeiras | Reportar Problemas |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/00-splash.png" width="200"> | <img src="docs/screenshots/01-inicio.png" width="200"> | <img src="docs/screenshots/02-mapa.png" width="200"> | <img src="docs/screenshots/03-reportar.png" width="200"> |

## Funcionalidades

- **Mapa de lixeiras:** exibe no Google Maps todas as lixeiras cadastradas e a sua posição atual, atualizada em tempo real.
- **Distância até cada lixeira:** calcula a distância entre você e cada lixeira (fórmula de Haversine), em metros ou quilômetros.
- **Lista interativa:** tocar em um item centraliza o mapa na lixeira; tocar em um marcador rola a lista até ela.
- **Rota a pé:** o botão **IR!** abre o Google Maps com a navegação a pé até a lixeira escolhida.
- **Reportar problemas:** informe se a lixeira sumiu, se está quebrada ou descreva outro problema. O relato é salvo no Firebase.

## Como funciona

```
┌──────────────┐     lixeiras (nome, local, coordenada)     ┌─────────────────────┐
│  TrashWay    │ ◄───────────────────────────────────────── │  Firebase Firestore │
│  (Android)   │ ──────────────────────────────────────────►│                     │
└──────┬───────┘     Problemas (relatos dos usuários)       └─────────────────────┘
       │
       ├── Google Maps SDK ........ mapa e marcadores
       └── Fused Location Provider  localização do usuário
```

- `SplashActivity` → tela de abertura, que leva à `MainActivity` com navegação inferior.
- `ui/home` → tela inicial com a apresentação do projeto.
- `ui/Mapa` → `DashboardFragment` (mapa + lista), `LixeiraViewModel` (leitura do Firestore e cálculo de distâncias) e `LixeiraAdapter`.
- `ui/problemas` → `NotificationsFragment`, formulário que grava na coleção `Problemas`.

## Tecnologias

- Kotlin · Android SDK (minSdk 26, targetSdk 34)
- Arquitetura MVVM com ViewModel + LiveData
- ViewBinding / DataBinding e Navigation Component
- Google Maps SDK for Android e Google Play Services Location
- Firebase Firestore e Firebase App Check

## Como executar

### Pré-requisitos
- Android Studio (ou Android SDK + JDK 17/21)
- Um projeto no [Firebase](https://console.firebase.google.com/) com o Firestore ativado
- Uma chave de API do [Google Maps SDK for Android](https://console.cloud.google.com/google/maps-apis)

### Configuração

As chaves **não** ficam no repositório. Antes de compilar:

1. **Chave do Google Maps:** adicione ao arquivo `local.properties`, na raiz do projeto (ele já é ignorado pelo Git):
   ```properties
   MAPS_API_KEY=sua_chave_aqui
   ```
2. **Firebase:** baixe o `google-services.json` do seu projeto Firebase e coloque-o em `app/google-services.json`. O arquivo `app/google-services.json.example` mostra o formato esperado.
3. **Dados:** crie no Firestore a coleção `lixeiras`, com documentos no formato:
   ```json
   { "nome": "Lixeira N°01", "local": "Av. Conselheiro Nébias 839", "coordenada": <GeoPoint> }
   ```

### Rodando
```bash
./gradlew installDebug
```
Ou abra a pasta no Android Studio e clique em **Run**. Conceda a permissão de localização para ver as lixeiras mais próximas.

> **Dica (emulador):** para simular uma posição em Santos, use `adb emu geo fix -46.3336 -23.9608`.
