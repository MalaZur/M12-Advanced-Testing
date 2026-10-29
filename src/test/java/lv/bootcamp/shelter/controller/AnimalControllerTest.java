package lv.bootcamp.shelter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lv.bootcamp.shelter.config.SecurityConfig;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.service.AnimalNotFoundException;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AnimalController.class)
@Import(SecurityConfig.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnimalService animalService;

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(animalService.findById(99L)).thenThrow(new AnimalNotFoundException(99L));

        mockMvc.perform(get("/api/animals/99")).andExpect(status().isNotFound());
    }

    @Test
    void findAll_shouldReturnAnimalAsJson() throws Exception{
        AnimalResponse first = new AnimalResponse(1L, "Rex", AnimalType.DOG, "Labrador", 3, "Angry", AnimalStatus.AVAILABLE);
        AnimalResponse second = new AnimalResponse(2L, "Max", AnimalType.CAT, "Siamese", 3, "Calm", AnimalStatus.ADOPTED);

        when(animalService.findAll()).thenReturn(List.of(first,second));

        mockMvc.perform(get("/api/animals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Rex"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Max"))
                .andExpect(jsonPath("$[1].status").value("ADOPTED"));
    }

    @Test
    void create_shouldRejectInvalidRequest() throws Exception{
        String invalidJson = """
                {"name": "",
                 "type": "DOG",
                 "breed": "Labrador",
                 "age": -3,
                 "description": "Friendly",
                 "microchipId": null}
                """;

        mockMvc.perform(post("/api/animals")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(animalService);
    }


    @Test
    void create_shouldReturn201AndAnimalAsJson() throws Exception {
        AnimalCreateRequest request = new AnimalCreateRequest(
                "Rex",
                AnimalType.DOG,
                "Labrador",
                3,
                "Friendly",
                "CHIP-001");

        AnimalResponse response = new AnimalResponse(
                1L,
                "Rex",
                AnimalType.DOG,
                "Labrador",
                3,
                "Friendly",
                AnimalStatus.AVAILABLE);

        when(animalService.create(any(AnimalCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/animals")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Rex"))
                .andExpect(jsonPath("$.type").value("DOG"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        verify(animalService).create(request);
    }

}
