package com.ParcAuto.Ensa.Affectation.Services;

import com.ParcAuto.Ensa.Affectation.Dto.DriverDTO;
import com.ParcAuto.Ensa.Affectation.Dto.TripDTO;
import com.ParcAuto.Ensa.Affectation.Dto.VehiculeDTO;
import com.ParcAuto.Ensa.Affectation.Entities.*;
import com.ParcAuto.Ensa.Affectation.Repositories.DriverRepository;
import com.ParcAuto.Ensa.Affectation.Repositories.TripRepository;
import com.ParcAuto.Ensa.Affectation.Repositories.VehiculeRepository;
import com.ParcAuto.Ensa.Affectation.Utils.PermitUtils;
import com.ParcAuto.Ensa.Affectation.mappers.DriverMappers;
import com.ParcAuto.Ensa.Affectation.mappers.VehiculeMappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AffectationService {
    private final TripService tripService;
    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final VehiculeRepository vehiculeRepository;


    @Autowired
    public AffectationService(TripService tripService , DriverRepository driverRepository , VehiculeRepository vehiculeRepository, TripRepository tripRepository) {
        this.tripService = tripService;
        this.tripRepository  = tripRepository;
        this.driverRepository = driverRepository;
        this.vehiculeRepository = vehiculeRepository;
    }

    public List<DriverDTO> getConducteursDisponibles(Long tripId) {
        TripDTO tripDTO = this.tripService.getTripById(tripId);
        VehiculeType vehiculeType = tripDTO.getVehiculType();
        PermisType permitType = PermitUtils.getPermisForVehiculeType(vehiculeType);
        Date departureDate = tripDTO.getDepartureDate();
        Date arrivalDate = tripDTO.getArrivalDate();
        Time departureTime = tripDTO.getDepartureTime();
        Time arrivalTime = tripDTO.getArrivalTime();

        // Searching for Driver :
        List<Driver> availableDrivers = this.getAvailableDrivers(permitType, departureDate, arrivalDate, departureTime, arrivalTime);
        if (availableDrivers == null || availableDrivers.isEmpty()) {
            return Collections.emptyList();
        }
        return availableDrivers.stream().map(DriverMappers::DriverToDTO).collect(Collectors.toList());
    }

    public List<VehiculeDTO> getVehiculesDisponibles(Long tripId) {
        TripDTO tripDTO = this.tripService.getTripById(tripId);
        VehiculeType vehiculeType = tripDTO.getVehiculType();
        PermisType permitType = PermitUtils.getPermisForVehiculeType(vehiculeType);
        Date departureDate = tripDTO.getDepartureDate();
        Date arrivalDate = tripDTO.getArrivalDate();
        Time departureTime = tripDTO.getDepartureTime();
        Time arrivalTime = tripDTO.getArrivalTime();

        // Searching for Vehicule :
        List<Vehicule> availableVehicles = this.filterAvailableVehicles(permitType, departureDate, arrivalDate, departureTime, arrivalTime);
        if (availableVehicles.isEmpty()) {
            return Collections.emptyList();
        }
        return availableVehicles.stream().map(VehiculeMappers::VehiculeToDTO).collect(Collectors.toList());
    }

    public String affecterTrip(Long driverId, Long vehiculeId, Long tripId) throws Exception {
        Optional<Trip> tripOptional = tripRepository.findById(tripId);
        Optional<Driver> driverOptional = driverRepository.findById(driverId);
        Optional<Vehicule> vehiculeOptional = vehiculeRepository.findById(vehiculeId);

        if (tripOptional.isEmpty() || driverOptional.isEmpty() || vehiculeOptional.isEmpty()) {
            return "Trip, driver, or vehicle not found!";
        }

        Trip trip = tripOptional.get();
        Driver driver = driverOptional.get();
        Vehicule vehicle = vehiculeOptional.get();

        if (!driver.isDisponibility() || !vehicle.isDisponibilite()) {
            return "already assigned trip!";
        }

        trip.setDriver(driver);
        trip.setVehicule(vehicle);

        driver.setDisponibility(false);
        vehicle.setDisponibilite(false);

        tripRepository.save(trip);
        driverRepository.save(driver);
        vehiculeRepository.save(vehicle);

        return "Trip assigned successfully!";
    }





    public List<Driver> getAvailableDrivers(PermisType permitType, Date departureDate, Date arrivalDate, Time departureTime, Time arrivalTime) {

        // Fetch all drivers from the repository
        List<Driver> allDrivers = driverRepository.findAll();


        return allDrivers.stream()
                .filter(driver -> {
                    // Check if the driver has the required permit type
                    Permis driverPermis = driver.getPermis();
                    if (driverPermis != null) {
                        boolean permitTypeMatches = driverPermis.getPermisRemises().stream()
                                .anyMatch(pr -> pr.getType() == permitType);

                        if (permitTypeMatches) {
                            // Check if the driver is available
                            boolean disponibility = driver.isDisponibility();

                            // Check if the driver's vacations do not overlap with the trip dates
                            boolean vacationsNotOverlapping = driver.getVacations().isEmpty() ||
                                    driver.getVacations().stream().noneMatch(vacation ->
                                            !(arrivalDate.before(vacation.getStart()) || departureDate.after(vacation.getEnd())));


                            // Check if the driver's trips do not overlap with the trip dates
                            boolean tripsNotOverlapping = driver.getTrips().stream()
                                    .noneMatch(trip ->
                                            filterDateTimeCritere(trip, departureDate, arrivalDate, departureTime, arrivalTime)
                                    );

                            return disponibility  && vacationsNotOverlapping && tripsNotOverlapping  ;

                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());

    }


    public List<Vehicule> filterAvailableVehicles(PermisType permitType, Date departureDate, Date arrivalDate, Time departureTime, Time arrivalTime) {
        List<Vehicule> vehicules = vehiculeRepository.findAll();
        return vehicules.stream()
                .filter(vehicule -> vehicule.isDisponibilite() && vehicule.getTypePermisRequis().equals(permitType))
                .filter(vehicule -> {
                    // Utilisation de NONE des véhicules qui ont des voyages prévus pendant les dates spécifiées
                    return vehicule.getTrips().stream()
                            .noneMatch(trip ->
                                    filterDateTimeCritere(trip, departureDate, arrivalDate, departureTime, arrivalTime)
                            );
                })
                .collect(Collectors.toList());
    }


    public boolean filterDateTimeCritere(Trip trip, Date departureDate, Date arrivalDate, Time departureTime, Time arrivalTime){
        return (departureDate.after(trip.getArrivalDate())
                || arrivalDate.before(trip.getDepartureDate())
                || trip.getDepartureDate().after(departureDate)
                && trip.getDepartureDate().before(arrivalDate)
                || trip.getArrivalDate().after(departureDate)
                && trip.getArrivalDate().before(arrivalDate)
                || departureTime.after(trip.getArrivalTime())
                || arrivalTime.before(trip.getDepartureTime())
                || trip.getDepartureTime().after(departureTime)
                && trip.getDepartureTime().before(arrivalTime)
                || trip.getArrivalTime().after(departureTime)
                && trip.getArrivalTime().before(arrivalTime));
    }

}

