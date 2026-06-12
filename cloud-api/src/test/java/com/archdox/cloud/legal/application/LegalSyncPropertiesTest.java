package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LegalSyncPropertiesTest {
    @Test
    void defaultOpenApiTargetsIncludeBuildingEnergyElectricalAndMechanicalCorpus() {
        var targets = new LegalSyncProperties().getOpenApi().getTargets();

        assertThat(targets)
                .extracting(LegalSyncProperties.Target::getActCode)
                .contains(
                        "BUILDING_ACT",
                        "BUILDING_ACT_ENFORCEMENT_DECREE",
                        "BUILDING_ACT_ENFORCEMENT_RULE",
                        "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD",
                        "GREEN_BUILDING_ACT",
                        "GREEN_BUILDING_ACT_ENFORCEMENT_DECREE",
                        "GREEN_BUILDING_ACT_ENFORCEMENT_RULE",
                        "BUILDING_ENERGY_SAVING_DESIGN_STANDARD",
                        "ELECTRICAL_SAFETY_MANAGEMENT_ACT",
                        "ELECTRICAL_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                        "ELECTRICAL_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                        "ELECTRICAL_EQUIPMENT_TECHNICAL_STANDARD",
                        "ELECTRICAL_EQUIPMENT_INSPECTION_AND_CHECK_NOTICE",
                        "KOREA_ELECTRICAL_CODE",
                        "ELECTRICAL_FACILITY_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                        "EMERGENCY_POWER_FACILITY_DESIGN_STANDARD",
                        "MECHANICAL_EQUIPMENT_ACT",
                        "MECHANICAL_EQUIPMENT_ACT_ENFORCEMENT_DECREE",
                        "MECHANICAL_EQUIPMENT_ACT_ENFORCEMENT_RULE",
                        "MECHANICAL_EQUIPMENT_TECHNICAL_STANDARD",
                        "BUILDING_EQUIPMENT_STANDARD_RULE",
                        "MECHANICAL_EQUIPMENT_MAINTENANCE_STANDARD",
                        "MECHANICAL_FACILITY_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                        "ELEVATOR_SAFETY_MANAGEMENT_ACT",
                        "ELEVATOR_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                        "ELEVATOR_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                        "ELEVATOR_SAFETY_PARTS_AND_ELEVATOR_SAFETY_STANDARD",
                        "ELEVATOR_INSTALLATION_AND_SAFETY_INSPECTION_OPERATION_RULE",
                        "MECHANICAL_PARKING_DEVICE_SAFETY_AND_INSPECTION_STANDARD",
                        "ELECTRICAL_CONSTRUCTION_BUSINESS_ACT",
                        "ELECTRICAL_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_DECREE",
                        "ELECTRICAL_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_RULE",
                        "ELECTRIC_POWER_TECHNOLOGY_MANAGEMENT_ACT",
                        "ELECTRIC_POWER_TECHNOLOGY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                        "ELECTRIC_POWER_TECHNOLOGY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                        "INFORMATION_COMMUNICATIONS_CONSTRUCTION_BUSINESS_ACT",
                        "INFORMATION_COMMUNICATIONS_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_DECREE",
                        "INFORMATION_COMMUNICATIONS_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_RULE",
                        "BROADCASTING_COMMUNICATIONS_FACILITY_TECHNICAL_STANDARD_DECREE",
                        "GROUNDING_AND_IN_BUILDING_COMMUNICATION_FACILITY_TECHNICAL_STANDARD",
                        "BROADCASTING_COMMUNAL_RECEPTION_FACILITY_INSTALLATION_STANDARD",
                        "CITY_GAS_BUSINESS_ACT",
                        "CITY_GAS_BUSINESS_ACT_ENFORCEMENT_DECREE",
                        "CITY_GAS_BUSINESS_ACT_ENFORCEMENT_RULE",
                        "LIQUEFIED_PETROLEUM_GAS_SAFETY_BUSINESS_ACT",
                        "LIQUEFIED_PETROLEUM_GAS_SAFETY_BUSINESS_ACT_ENFORCEMENT_DECREE",
                        "LIQUEFIED_PETROLEUM_GAS_SAFETY_BUSINESS_ACT_ENFORCEMENT_RULE",
                        "HIGH_PRESSURE_GAS_SAFETY_CONTROL_ACT",
                        "HIGH_PRESSURE_GAS_SAFETY_CONTROL_ACT_ENFORCEMENT_DECREE",
                        "HIGH_PRESSURE_GAS_SAFETY_CONTROL_ACT_ENFORCEMENT_RULE",
                        "NEW_RENEWABLE_ENERGY_ACT",
                        "NEW_RENEWABLE_ENERGY_ACT_ENFORCEMENT_DECREE",
                        "NEW_RENEWABLE_ENERGY_ACT_ENFORCEMENT_RULE",
                        "FIRE_FACILITIES_INSTALLATION_MANAGEMENT_ACT",
                        "FIRE_FACILITIES_INSTALLATION_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                        "FIRE_FACILITIES_INSTALLATION_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                        "FIRE_PREVENTION_SAFETY_MANAGEMENT_ACT",
                        "FIRE_PREVENTION_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                        "FIRE_PREVENTION_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                        "BUILDING_EVACUATION_FIREPROOF_STRUCTURE_RULE",
                        "BUILDING_MATERIAL_QUALITY_RECOGNITION_MANAGEMENT_STANDARD",
                        "FIRE_RESISTANT_STRUCTURE_RECOGNITION_MANAGEMENT_STANDARD",
                        "AUTOMATIC_FIRE_SHUTTER_AND_FIRE_DOOR_STANDARD",
                        "PARKING_LOT_ACT",
                        "PARKING_LOT_ACT_ENFORCEMENT_DECREE",
                        "PARKING_LOT_ACT_ENFORCEMENT_RULE",
                        "DISABLED_ELDERLY_PREGNANT_ACCESSIBILITY_ACT",
                        "DISABLED_ELDERLY_PREGNANT_ACCESSIBILITY_ACT_ENFORCEMENT_DECREE",
                        "DISABLED_ELDERLY_PREGNANT_ACCESSIBILITY_ACT_ENFORCEMENT_RULE",
                        "BUILDING_ENERGY_EFFICIENCY_ZERO_ENERGY_CERTIFICATION_RULE",
                        "GREEN_BUILDING_CERTIFICATION_RULE",
                        "GREEN_BUILDING_CERTIFICATION_STANDARD",
                        "BUILDING_ENERGY_EFFICIENCY_ZERO_ENERGY_CERTIFICATION_STANDARD",
                        "CONSTRUCTION_TECHNOLOGY_PROMOTION_ACT",
                        "CONSTRUCTION_TECHNOLOGY_PROMOTION_ACT_ENFORCEMENT_DECREE",
                        "CONSTRUCTION_TECHNOLOGY_PROMOTION_ACT_ENFORCEMENT_RULE",
                        "CONSTRUCTION_WORK_QUALITY_MANAGEMENT_GUIDELINE",
                        "CONSTRUCTION_WORK_SAFETY_MANAGEMENT_GUIDELINE",
                        "CONSTRUCTION_PROJECT_MANAGEMENT_METHOD_REVIEW_AND_WORK_GUIDELINE",
                        "BUILDING_STRUCTURAL_STANDARD_RULE",
                        "BUILDING_STRUCTURAL_DESIGN_STANDARD",
                        "ARCHITECTURAL_WORK_STANDARD_SPECIFICATION",
                        "STEEL_STRUCTURE_WORK_STANDARD_SPECIFICATION",
                        "CONCRETE_STRUCTURE_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                        "TEMPORARY_FACILITY_DESIGN_AND_TEMPORARY_WORK_STANDARD_SPECIFICATION",
                        "GEOTECHNICAL_DESIGN_AND_GROUND_WORK_STANDARD_SPECIFICATION",
                        "LANDSCAPE_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                        "OCCUPATIONAL_SAFETY_HEALTH_ACT",
                        "OCCUPATIONAL_SAFETY_HEALTH_ACT_ENFORCEMENT_DECREE",
                        "OCCUPATIONAL_SAFETY_HEALTH_ACT_ENFORCEMENT_RULE",
                        "OCCUPATIONAL_SAFETY_HEALTH_STANDARDS_RULE",
                        "EXCAVATION_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                        "STEEL_FRAME_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                        "CONCRETE_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                        "TEMPORARY_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                        "DEMOLITION_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                        "UNDERGROUND_SAFETY_MANAGEMENT_SPECIAL_ACT",
                        "UNDERGROUND_SAFETY_MANAGEMENT_SPECIAL_ACT_ENFORCEMENT_DECREE",
                        "UNDERGROUND_SAFETY_MANAGEMENT_SPECIAL_ACT_ENFORCEMENT_RULE",
                        "UNDERGROUND_SAFETY_MANAGEMENT_WORK_GUIDELINE");
    }

    @Test
    void expandedOpenApiTargetsUseLawAndAdministrativeRuleEndpointsOnly() {
        var targets = new LegalSyncProperties().getOpenApi().getTargets();

        assertThat(targets)
                .allSatisfy(target -> assertThat(target.getTarget()).isIn("law", "admrul"));
        assertThat(targets)
                .filteredOn(target -> "BUILDING_ENERGY_SAVING_DESIGN_STANDARD".equals(target.getActCode()))
                .singleElement()
                .satisfies(target -> {
                    assertThat(target.getTarget()).isEqualTo("admrul");
                    assertThat(target.getActType()).isEqualTo("ADMINISTRATIVE_RULE");
                });
        assertThat(targets)
                .filteredOn(target -> "ELECTRICAL_SAFETY_MANAGEMENT_ACT".equals(target.getActCode()))
                .singleElement()
                .satisfies(target -> {
                    assertThat(target.getTarget()).isEqualTo("law");
                    assertThat(target.getActType()).isEqualTo("LAW");
                });
    }
}
