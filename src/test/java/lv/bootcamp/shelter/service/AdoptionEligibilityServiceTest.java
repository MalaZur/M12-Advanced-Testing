
package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;


    @Test
    void evaluate_shouldRejectWhenAdopterNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ADOPTER_NOT_FOUND);
        verifyNoInteractions(animalRepository, auditLogger);
    }

    @Test
    void evaluate_shouldRejectWhenAnimalNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(newAdopter()));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ANIMAL_NOT_FOUND);
        verifyNoInteractions(auditLogger);
    }

    @Test
    void evaluate_shouldRejectWhenAnimalNotAvailable() {
        Animal animal = newAnimal(AnimalType.DOG);
        animal.setStatus(AnimalStatus.ADOPTED);
        stubFound(newAdopter(), animal);

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ANIMAL_NOT_AVAILABLE);
        verifyNoInteractions(auditLogger);
    }

    @Test
    void evaluate_shouldRejectUnderageAdopter() {
        Adopter adopter = newAdopter();
        adopter.setAge(17);
        stubFound(adopter, newAnimal(AnimalType.DOG));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
    }

    @Test
    void evaluate_shouldAllowAge18AndTwoPets() {
        Adopter adopter = newAdopter();
        adopter.setAge(18);
        adopter.setCurrentPetCount(2);
        adopter.setPreviousAdoptions(1);
        stubFound(adopter, newAnimal(AnimalType.DOG));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertApproved(result, 6);
    }

    @Test
    void evaluate_shouldRejectThreePetsOnNormalProperty() {
        Adopter adopter = newAdopter();
        adopter.setCurrentPetCount(3);
        stubFound(adopter, newAnimal(AnimalType.DOG));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
    }

    @Test
    void evaluate_shouldRejectFivePetsOnLargeProperty() {
        Adopter adopter = newAdopter();
        adopter.setLargeProperty(true);
        adopter.setCurrentPetCount(5);
        stubFound(adopter, newAnimal(AnimalType.DOG));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
    }

    @Test
    void evaluate_shouldAllowFourPetsOnLargeProperty() {
        Adopter adopter = newAdopter();
        adopter.setLargeProperty(true);
        adopter.setCurrentPetCount(4);
        stubFound(adopter, newAnimal(AnimalType.DOG));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertApproved(result, 7);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void evaluate_shouldRejectExoticWithoutPermit(AnimalType type) {
        stubFound(newAdopter(), newAnimal(type));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.NO_EXOTIC_PERMIT);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void evaluate_shouldAllowExoticWithPermit(AnimalType type) {
        Adopter adopter = newAdopter();
        adopter.setExoticPermit(true);
        stubFound(adopter, newAnimal(type));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertApproved(result, 0);
    }

    @Test
    void evaluate_shouldAllowDogWithoutExoticPermit() {
        stubFound(newAdopter(), newAnimal(AnimalType.DOG));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertApproved(result, 0);
    }

    @Test
    void calculatePriorityScore_shouldRespectBonusBoundaries() {
        Adopter adopter = newAdopter();
        Animal animal = newAnimal(AnimalType.DOG);
        animal.setAge(7);

        assertThat(service.calculatePriorityScore(adopter, animal)).isZero();

        adopter.setPreviousAdoptions(1);
        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(10);

        adopter.setPreviousAdoptions(3);
        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(10);

        adopter.setPreviousAdoptions(4);
        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(15);

        animal.setAge(8);
        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(35);

        adopter.setLargeProperty(true);
        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(50);

        adopter.setCurrentPetCount(2);
        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(46);
    }

    @Test
    void calculatePriorityScore_shouldHandleNullAnimalAge() {
        Animal animal = newAnimal(AnimalType.DOG);
        animal.setAge(null);

        assertThat(service.calculatePriorityScore(newAdopter(), animal)).isZero();
    }

    @Test
    void calculatePriorityScore_shouldNeverBeNegative() {
        Adopter adopter = newAdopter();
        adopter.setCurrentPetCount(2);

        assertThat(service.calculatePriorityScore(adopter, newAnimal(AnimalType.DOG))).isZero();
    }

    private Adopter newAdopter() {
        Adopter adopter = new Adopter();
        adopter.setId(ADOPTER_ID);
        adopter.setName("Anna");
        adopter.setEmail("anna@example.com");
        adopter.setAge(25);
        return adopter;
    }

    private Animal newAnimal(AnimalType type) {
        Animal animal = new Animal();
        animal.setId(ANIMAL_ID);
        animal.setName("Rex");
        animal.setType(type);
        animal.setAge(3);
        animal.setStatus(AnimalStatus.AVAILABLE);
        return animal;
    }

    private void stubFound(Adopter adopter, Animal animal) {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    private void assertRejected(AdoptionResult result, String reason) {
        assertThat(result).usingRecursiveComparison().isEqualTo(AdoptionResult.rejected(reason));

        verifyNoInteractions(notificationClient);
    }

    private void assertApproved(AdoptionResult result, int score) {
        assertThat(result).usingRecursiveComparison().isEqualTo(AdoptionResult.approved(score));

        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, score);

        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
    }
}
