# Przepisany System Sieci Płynów (Integrated Fluid Network)

## Przegląd

System sieci płynów został całkowicie przepisany od podstaw, aby wyeliminować błędy związane z łączeniem/rozłączaniem kabli i nieprawidłowym trasowaniem sieci.

## Architektura

### 1. **NetworkManager** (Nowy)
Centralizowany menedżer sieci, który zarządza wszystkimi sieciami w świecie:
- **Singleton per World**: Każdy świat ma własną instancję managera
- **Graph-based approach**: Używa grafów do reprezentacji sieci
- **Flood-fill algorithm**: Automatyczne wykrywanie połączonych komponentów
- **Event-driven**: Reaguje na zdarzenia (dodanie, usunięcie, zmiana połączenia)

### 2. **IntegratedFluidNetwork** (Bez zmian)
Reprezentuje pojedynczą sieć płynów:
- Przechowuje płyn, ciśnienie i temperaturę
- Zarządza członkami sieci (pipes i hatches)
- Oblicza pojemność dynamicznie

### 3. **IIntegratedFluidMember** (Bez zmian)
Interfejs dla komponentów sieci (pipes i hatches)

### 4. **MTEIntegratedFluidPipe** (Zmodyfikowany)
Teraz deleguje całą logikę sieci do NetworkManager:
- `onFirstTick()`: Rejestruje się w NetworkManager
- `onWrenchRightClick()`: Powiadamia NetworkManager o zmianie połączenia
- `onRemoval()`: Powiadamia NetworkManager o usunięciu
- `rebuildNetwork()`: DEPRECATED - deleguje do NetworkManager

### 5. **MTEIntegratedFluidInputHatch/OutputHatch/InjectorHatch** (Zmodyfikowane)
Podobnie jak pipes, delegują logikę do NetworkManager

## Kluczowe Ulepszenia

### 1. **Poprawne Scalanie Sieci**
- Używa flood-fill do znalezienia wszystkich połączonych komponentów
- Automatycznie scala sieci przy połączeniu
- Właściwie łączy płyny z zachowaniem średniej temperatury

### 2. **Poprawne Dzielenie Sieci**
- Automatycznie wykrywa rozłączone komponenty
- Dzieli sieć na wiele mniejszych sieci
- Proporcjonalnie dzieli płyn według pojemności

### 3. **Eliminacja Duplikacji Płynów**
- NetworkManager śledzi wszystkie sieci
- Zapobiega tworzeniu duplikatów podczas łączenia
- Zachowuje całkowitą ilość płynu podczas podziału

### 4. **Deterministyczne Zachowanie**
- Wszystkie operacje są atomowe
- Kolejność operacji jest spójna
- Brak warunków wyścigu

## Algorytmy

### Flood-Fill (Znajdowanie Połączonych Komponentów)
```java
1. Rozpocznij od węzła startowego
2. Dodaj do kolejki
3. Dopóki kolejka nie jest pusta:
   a. Pobierz węzeł z kolejki
   b. Dodaj do komponentu
   c. Znajdź wszystkich sąsiadów
   d. Dodaj nieodwiedzonych sąsiadów do kolejki
4. Zwróć komponent
```

### Scalanie Sieci
```java
1. Zbierz wszystkie sieci sąsiadów
2. Jeśli brak sieci: Utwórz nową
3. Jeśli jedna sieć: Dodaj do niej
4. Jeśli wiele sieci:
   a. Wybierz największą jako główną
   b. Scal wszystkie inne do głównej
   c. Połącz płyny z średnią ważoną temperatury
```

### Dzielenie Sieci
```java
1. Usuń członka z sieci
2. Znajdź wszystkich pozostałych członków
3. Użyj flood-fill do znalezienia komponentów
4. Jeśli jeden komponent: Sieć jest nadal połączona
5. Jeśli wiele komponentów:
   a. Utwórz nową sieć dla każdego komponentu
   b. Podziel płyn proporcjonalnie według pojemności
   c. Zachowaj ciśnienie i temperaturę
```

## Użycie

### Dodawanie Nowego Członka
```java
NetworkManager manager = NetworkManager.getInstance(world);
manager.onMemberAdded(member);
```

### Usuwanie Członka
```java
NetworkManager manager = NetworkManager.getInstance(world);
manager.onMemberRemoved(member);
```

### Zmiana Połączenia (Łączenie/Rozłączanie Kabla)
```java
NetworkManager manager = NetworkManager.getInstance(world);
manager.onConnectionChanged(member);
```

## Testowanie

### Scenariusze Testowe
1. **Podstawowe Połączenie**: Połącz dwa hatches rurą
2. **Scalanie**: Połącz dwie oddzielne sieci
3. **Dzielenie**: Usuń rurę w środku sieci
4. **Wrench Disconnect**: Rozłącz rurę kluczem
5. **Wrench Connect**: Połącz rozłączoną rurę
6. **Złożona Topologia**: Testuj rozgałęzione sieci

### Sprawdzenia
- [ ] Brak duplikacji płynów
- [ ] Płyn jest prawidłowo dzielony przy rozłączeniu
- [ ] Temperatura jest zachowana
- [ ] Ciśnienie jest zachowane
- [ ] Pojemność jest obliczana poprawnie
- [ ] Brak wycieków pamięci (WeakHashMap dla world instances)

## Migracja

Stary kod jest oznaczony jako `@Deprecated` ale nadal działa (deleguje do NetworkManager).
To zapewnia kompatybilność wsteczną podczas przejścia.

### Deprecated Methods
- `MTEIntegratedFluidPipe.rebuildNetwork()`
- `MTEIntegratedFluidInputHatch.findAndJoinNetwork()`
- `MTEIntegratedFluidInjectorHatch.findAndJoinNetwork()`
- `MTEIntegratedFluidOutputHatch.findAndJoinNetwork()`

## Wydajność

### Optymalizacje
- **WeakHashMap**: Automatyczne czyszczenie dla unloaded worlds
- **HashSet/HashMap**: O(1) operacje dodawania/usuwania
- **Flood-fill**: O(N) gdzie N to liczba członków w komponencie
- **Lazy initialization**: NetworkManager tworzony tylko gdy potrzebny

### Złożoność Czasowa
- `onMemberAdded()`: O(N) - flood-fill dla znalezienia komponentu
- `onMemberRemoved()`: O(N × K) - K komponentów, N członków każdy
- `onConnectionChanged()`: O(N × K) - pełna przebudowa

## Znane Ograniczenia

1. **Nie wykrywa cykli**: System nie optymalizuje dla cyklicznych sieci
2. **Brak priorytetyzacji**: Wszystkie hatches są traktowane równo
3. **Synchroniczne operacje**: Wszystkie operacje są natychmiastowe (brak async)

## Przyszłe Ulepszenia

1. **Cache topologii**: Przechowuj graf dla szybszych zapytań
2. **Incremental updates**: Aktualizuj tylko zmienione części
3. **Network visualization**: Debug mode do wizualizacji sieci
4. **Performance metrics**: Śledź wydajność operacji sieciowych
5. **Event bus**: Lepsze powiadamianie o zmianach sieci

## Debugowanie

### Logi
NetworkManager może być rozszerzony o logowanie dla debugowania:
```java
if (DEBUG) {
    System.out.println("Network split into " + components.size() + " components");
}
```

### WAILA/Overlay
Pipes i hatches wyświetlają:
- Ilość płynu w sieci
- Liczbę członków
- Ciśnienie i temperaturę
- ID sieci (UUID)

## Autorzy

Przepisane przez: GitHub Copilot (AI Assistant)
Data: 2025-12-06
Wersja: 1.0.0

## Licencja

Zgodne z licencją projektu GT5-Unofficial.

