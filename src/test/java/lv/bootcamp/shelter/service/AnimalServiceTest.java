package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Captor
    private ArgumentCaptor<List<Long>> animalIdsCaptor;

    @InjectMocks
    private AnimalService animalService;

    @Test
    void create_shouldSaveAnimalWithAvailableStatus() {
        Animal saved = new Animal();
        saved.setId(1L);
        saved.setName("Rex");
        saved.setType(AnimalType.DOG);
        saved.setStatus(AnimalStatus.AVAILABLE);

        when(animalRepository.save(any(Animal.class))).thenReturn(saved);

        AnimalResponse response = animalService.create(
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Labrador", 3, "Friendly", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Rex");
        assertThat(response.status()).isEqualTo(AnimalStatus.AVAILABLE);
    }

    @Test
    void findById_shouldThrowWhenAnimalNotFound() {
        when(animalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalService.findById(99L))
                .isInstanceOf(AnimalNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void adopt_shouldThrowWhenAnimalAlreadyAdopted() {
        Animal adopted = new Animal();
        adopted.setId(1L);
        adopted.setStatus(AnimalStatus.ADOPTED);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(adopted));

        assertThatThrownBy(() -> animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adopt_shouldChangeStatusAndSendNotification(){

        Animal animal = new Animal();
        animal.setId(1L);
        animal.setName("Rex");
        animal.setType(AnimalType.DOG);
        animal.setStatus(AnimalStatus.AVAILABLE);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));
        when(animalRepository.save(any(Animal.class))).thenReturn(animal);

        AnimalResponse response = animalService.adopt(new AdoptionRequest(1L, "Anna", "anna@example.com"));

        assertThat(response.status()).isEqualTo(AnimalStatus.ADOPTED);
        assertThat(animal.getStatus()).isEqualTo(AnimalStatus.ADOPTED);

        verify(animalRepository).save(animal);
        verify(notificationClient).sendAdoptionNotification(1L, "Rex", "anna@example.com");
    }

    @Test
    void reserveMultiple_shouldNotifyWithExactReservedIds(){

        Animal first = new Animal();
        first.setId(1L);
        first.setName("Rex");
        first.setStatus(AnimalStatus.AVAILABLE);

        Animal second = new Animal();
        second.setId(2L);
        second.setName("Max");
        second.setStatus(AnimalStatus.AVAILABLE);

        List<Long> ids = List.of(1L, 2L);

        when(animalRepository.findAllById(ids)).thenReturn(List.of(first, second));
        when(animalRepository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AnimalResponse> responses = animalService.reserveMultiple(ids);

        assertThat(responses).hasSize(2);
        assertThat(first.getStatus()).isEqualTo(AnimalStatus.RESERVED);
        assertThat(second.getStatus()).isEqualTo(AnimalStatus.RESERVED);

        verify(animalRepository, times(2)).save(any(Animal.class));
        verify(notificationClient).sendBulkStatusNotification(animalIdsCaptor.capture(), eq("RESERVED"));
        assertThat(animalIdsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void create_shouldRejectDuplicateMicrochipWithoutSaving(){
        String microchipId = "CHIP-001";

        when(animalRepository.existsByMicrochipId(microchipId)).thenReturn(true);

        AnimalCreateRequest request = new AnimalCreateRequest("Rex", AnimalType.DOG, "Labrador", 3, "Angry", microchipId);

        assertThatThrownBy(() -> animalService.create(request)).isInstanceOf(IllegalStateException.class).hasMessageContaining(microchipId);

        verify(animalRepository).existsByMicrochipId(microchipId);
        verify(animalRepository, never()).save(any(Animal.class));
    }
}
