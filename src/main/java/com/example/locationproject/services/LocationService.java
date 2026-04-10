package com.example.locationproject.services;

import com.example.locationproject.dtos.*;
import com.example.locationproject.entities.Contact;
import com.example.locationproject.entities.Marker;
import com.example.locationproject.entities.TranslateDescription;
import com.example.locationproject.enums.MarkerType;
import com.example.locationproject.exception.ResourceNotFoundException;
import com.example.locationproject.repositories.ContactRepository;
import com.example.locationproject.repositories.MarkerRepository;
import com.example.locationproject.repositories.TranslateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
public class LocationService {

    private final MarkerRepository markerRepo;
    private final ModelMapper mapper;
    private final ContactRepository contactRepository;
    private final TranslateRepository translateRepository;

    public ResponseDto createMarker(RequestDto requestDto) {
        log.info("Request: {}", requestDto);
        MarkerType markerType = MarkerType.fromString(String.valueOf(requestDto.getMarkerType()));

        Marker marker = new Marker();
        marker.setTitle(requestDto.getTitle());
        marker.setDescription(requestDto.getDescription());
        marker.setMarkerType(markerType);
        marker.setLatitude(requestDto.getLatitude());
        marker.setLongitude(requestDto.getLongitude());

        marker = markerRepo.save(marker);
        return mapper.map(marker, ResponseDto.class);
    }

    public ResponseDto getMarker(Long id) {
        Marker marker = markerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
        return mapper.map(marker, ResponseDto.class);
    }

    public List<ResponseDto> getMarkersByTitle(String title) {
//        List<Marker> markers = markerRepo.findByTitleIgnoreCase(title);
//        return markers.stream()
//                .map(marker -> mapper.map(marker, ResponseDto.class))
//                .collect(Collectors.toList());
        List<Marker> markers = markerRepo.findByTitleContainsIgnoreCase(title);
        return markers.stream()
                .map(marker -> mapper.map(marker, ResponseDto.class))
                .collect(Collectors.toList());
    }

    //    public List<ResponseDto> getAllMarkers() {
//        List<Marker> markers = markerRepo.findAllWithTranslations();
//        return listMapping(markers, ResponseDto.class);
//    }
    public List<ResponseDto> getAllMarkers() {
        List<Marker> markers = markerRepo.findAllWithTranslations();
        return markers.stream()
                .map(this::mapMarkerToResponseDto) // используем кастомный метод маппинга
                .collect(Collectors.toList());
    }

    public List<ResponseDto> getDuplicateMarkers() {
        List<Marker> dupMarkers = markerRepo.findDuplicateMarkers();
        return listMapping(dupMarkers, ResponseDto.class);
    }


    @Transactional
    public void deleteAllDuplicates() {
        List<Marker> duplicateMarkers = markerRepo.findDuplicateMarkers();

        var groupedMarkers = duplicateMarkers.stream()
                .filter(marker -> marker.getMarkerType() == MarkerType.CUSTOM) // Only CUSTOM type
                .collect(Collectors.groupingBy(marker -> marker.getTitle() + "|"
                        + marker.getDescription() + "|"
                        + marker.getLatitude() + "|"
                        + marker.getLongitude() + "|"
                        + marker.getMarkerType()));

        for (var entry : groupedMarkers.entrySet()) {
            List<Marker> markers = entry.getValue();

            markers.sort((m1, m2) -> Long.compare(m2.getId(), m1.getId()));

            if (markers.size() > 1) {
                List<Marker> markersToDelete = markers.subList(1, markers.size());
                markerRepo.deleteAll(markersToDelete);
            }
        }
    }


    public ResponseDto updateMarkerType(Long id, MarkerType markerType) {
        Marker existingMarker = markerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marker not found with id: " + id));

        existingMarker.setMarkerType(markerType);

        markerRepo.save(existingMarker);

        return mapper.map(existingMarker, ResponseDto.class);
    }


    public ResponseDto updateMarker(Long id, RequestDto requestDto) {
        Marker existingMarker = markerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
        mapper.map(requestDto, existingMarker);
        markerRepo.save(existingMarker);
        return mapper.map(existingMarker, ResponseDto.class);
    }


    public ResponseDto updateMarkerDescription(Long id, String newDescription) {
        Marker existingMarker = markerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marker not found with id: " + id));
        existingMarker.setDescription(newDescription);
        markerRepo.save(existingMarker);
        return mapper.map(existingMarker, ResponseDto.class);
    }


    public ResponseDto updateMarkerName(Long id, String newName) {
        Marker existingMarker = markerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marker not found with id: " + id));
        existingMarker.setTitle(newName);
        markerRepo.save(existingMarker);
        return mapper.map(existingMarker, ResponseDto.class);
    }


    public ResponseDto deleteMarker(Long id) {
        Marker marker = markerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
        markerRepo.delete(marker);
        return mapper.map(marker, ResponseDto.class);
    }


    public <D, S> List<D> listMapping(List<S> source, Class<D> destination) {
        return source.stream().map(s -> mapper.map(s, destination)).toList();
    }

    public String saveContact(ContactDTO contactDTO) {
        if (contactDTO.getEmail() == null) {
            throw new IllegalArgumentException("zəhmət olmasa email bölməsini boş saxlamayın.");
        }
        log.info("Request: {}", contactDTO);

        Contact contact = new Contact();
        contact.setName(contactDTO.getName());
        contact.setSurname(contactDTO.getSurname());
        contact.setEmail(contactDTO.getEmail());
        contact.setContactNumber(contactDTO.getContactNumber());
        contact.setDescription(contactDTO.getDescription());

        contact = contactRepository.save(contact);
        mapper.map(contact, ContactResponseDTO.class);
        return "göndərildi";
    }


    public List<ContactResponseDTO> getAllContacts() {
        List<Contact> contacts = contactRepository.findAll();
        return listMapping(contacts, ContactResponseDTO.class);
    }

    public ContactResponseDTO deleteMessage(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("message not found : " + id));
        contactRepository.delete(contact);
        return mapper.map(contact, ContactResponseDTO.class);
    }


    @Transactional
    public void deleteAllContacts() {
        log.info("Deleting all contacts");
        contactRepository.deleteAll();
    }


    public ResponseTranslate addTranslationToMarker(Long markerId, RequestTranslate translate) {
        Marker marker = markerRepo.findById(markerId)
                .orElseThrow(() -> new ResourceNotFoundException("Marker not found with id: " + markerId));

        TranslateDescription translation = new TranslateDescription();
        translation.setLanguageCode(translate.getLanguageCode());
        translation.setDescription(translate.getDescription());
        translation.setMarker(marker);

        translateRepository.save(translation);
        return mapper.map(translation, ResponseTranslate.class);
    }


    public List<ResponseTranslate> getAllTranslate() {
        List<TranslateDescription> translateDescriptions = translateRepository.findAll();
        return listMapping(translateDescriptions, ResponseTranslate.class);
    }


    public ResponseTranslate deleteTranslate(Long id) {
        TranslateDescription translateDescription = translateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Translate not found with id: " + id));
        translateRepository.delete(translateDescription);
        return mapper.map(translateDescription, ResponseTranslate.class);
    }

    public ResponseDto mapMarkerToResponseDto(Marker marker) {
        ResponseDto dto = mapper.map(marker, ResponseDto.class);
        List<ResponseTranslate> translationDtos = marker.getTranslations().stream()
                .map(t -> mapper.map(t, ResponseTranslate.class))
                .collect(Collectors.toList());
        dto.setTranslations(translationDtos);
        return dto;
    }


}
