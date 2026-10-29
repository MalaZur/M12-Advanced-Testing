package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        LocalDateTime since = LocalDateTime.of(2026, 10, 1, 12, 0);

        Animal dog1 = newAnimal("Rex", AnimalType.DOG);
        Animal dog2 = newAnimal("Max", AnimalType.DOG);
        Animal dog3 = newAnimal("Buddy", AnimalType.DOG);
        Animal cat = newAnimal("Luna", AnimalType.CAT);

        entityManager.persist(dog1);
        entityManager.persist(dog2);
        entityManager.persist(dog3);
        entityManager.persist(cat);

        entityManager.persist(newRecord(dog1, "anna@example.com", since.plusDays(1)));

        entityManager.persist(newRecord(dog2, "mark@example.com", since));

        entityManager.persist(newRecord(dog3, "old@example.com", since.minusDays(1)));

        entityManager.persist(newRecord(cat, "cat@example.com", since.plusDays(1)));

        entityManager.flush();
        entityManager.clear();

        List<AdoptionRecord> results = adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, since);

        assertThat(results).hasSize(2);

        assertThat(results)
                .extracting(AdoptionRecord::getAdopterEmail)
                .containsExactlyInAnyOrder(
                        "anna@example.com",
                        "mark@example.com");
    }


    @Test
    void findByAdopterEmailDomain_shouldReturnOnlyMatchingEmails() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);

        Animal dog = newAnimal("Rex", AnimalType.DOG);
        Animal cat = newAnimal("Luna", AnimalType.CAT);
        Animal bird = newAnimal("Kiwi", AnimalType.BIRD);
        Animal rabbit = newAnimal("Max", AnimalType.RABBIT);

        entityManager.persist(dog);
        entityManager.persist(cat);
        entityManager.persist(bird);
        entityManager.persist(rabbit);

        entityManager.persist(newRecord(dog, "anna@gmail.com", now));

        entityManager.persist(newRecord(cat, "mark@GMAIL.COM", now));

        entityManager.persist(newRecord(bird, "john@yahoo.com", now));

        entityManager.persist(newRecord(rabbit, "alex@notgmail.com", now));

        entityManager.flush();
        entityManager.clear();

        List<AdoptionRecord> results = adoptionRecordRepository.findByAdopterEmailDomain("gmail.com");

        assertThat(results).hasSize(2);

        assertThat(results)
                .extracting(AdoptionRecord::getAdopterEmail)
                .containsExactlyInAnyOrder("anna@gmail.com", "mark@GMAIL.COM");
    }

    private Animal newAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        return animal;
    }

    private AdoptionRecord newRecord(
            Animal animal,
            String email,
            LocalDateTime date) {

        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(email);
        record.setAdoptedAt(date);
        return record;
    }
}
