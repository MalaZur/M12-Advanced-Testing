package lv.bootcamp.shelter;

import jakarta.persistence.EntityManager;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class AdoptionIntegrationTest {

    @Autowired
    private AnimalService animalService;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private NotificationClient notificationClient;

    @Test
    void createAndAdopt_shouldCompleteFullAdoptionFlow() {
        AnimalCreateRequest createRequest = new AnimalCreateRequest(
                "Rex",
                AnimalType.DOG,
                "Labrador",
                3,
                "Friendly",
                "CHIP-001");

        AnimalResponse created = animalService.create(createRequest);

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(AnimalStatus.AVAILABLE);

        Animal savedAnimal = animalRepository.findById(created.id()).orElseThrow();

        assertThat(savedAnimal.getName()).isEqualTo("Rex");

        AdoptionRequest adoptionRequest = new AdoptionRequest(created.id(), "Anna", "anna@example.com");

        AnimalResponse adopted = animalService.adopt(adoptionRequest);

        assertThat(adopted.status()).isEqualTo(AnimalStatus.ADOPTED);

        entityManager.flush();
        entityManager.clear();

        Animal updatedAnimal = animalRepository.findById(created.id()).orElseThrow();

        assertThat(updatedAnimal.getStatus()).isEqualTo(AnimalStatus.ADOPTED);

        verify(notificationClient).sendAdoptionNotification(created.id(), "Rex", "anna@example.com");
    }
}
