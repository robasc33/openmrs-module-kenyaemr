/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.kenyaemr.fragment.controller.patient;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PatientProgram;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Program;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.kenyaemr.validator.TelephoneNumberValidator;
import org.openmrs.module.kenyaemr.wrapper.PatientWrapper;
import org.openmrs.module.kenyaemr.wrapper.PersonWrapper;
import org.openmrs.module.kenyaui.form.AbstractWebForm;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.module.kenyaemr.Dictionary;
import org.openmrs.module.kenyaemr.metadata.CommonMetadata;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.kenyaemr.api.KenyaEmrService;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.BindParams;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.MethodParam;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for creating and editing patients in the registration app
 */
public class EditPatientFragmentController {

	// We don't record cause of death, but data model requires a concept
	private static final String CAUSE_OF_DEATH_PLACEHOLDER = Dictionary.UNKNOWN;

	/**
	 * Main controller method
	 * @param patient the patient (may be null)
	 * @param person the person (may be null)
	 * @param model the model
	 */
	public void controller(@FragmentParam(value = "patient", required = false) Patient patient,
						   @FragmentParam(value = "person", required = false) Person person,
						   FragmentModel model) {

		if (patient != null && person != null) {
			throw new RuntimeException("A patient or person can be provided, but not both");
		}

		Person existing = patient != null ? patient : person;

		model.addAttribute("command", newEditPatientForm(existing));

		model.addAttribute("civilStatusConcept", Dictionary.getConcept(Dictionary.CIVIL_STATUS));
		model.addAttribute("occupationConcept", Dictionary.getConcept(Dictionary.OCCUPATION));
		model.addAttribute("educationConcept", Dictionary.getConcept(Dictionary.EDUCATION));

		// Create list of education answer concepts
		List<Concept> educationOptions = new ArrayList<Concept>();
		educationOptions.add(Dictionary.getConcept(Dictionary.NONE));
		educationOptions.add(Dictionary.getConcept(Dictionary.PRIMARY_EDUCATION));
		educationOptions.add(Dictionary.getConcept(Dictionary.SECONDARY_EDUCATION));
		educationOptions.add(Dictionary.getConcept(Dictionary.COLLEGE_UNIVERSITY_POLYTECHNIC));
		model.addAttribute("educationOptions", educationOptions);

		// Create a list of marital status answer concepts
		List<Concept> maritalStatusOptions = new ArrayList<Concept>();
		maritalStatusOptions.add(Dictionary.getConcept(Dictionary.MARRIED_POLYGAMOUS));
		maritalStatusOptions.add(Dictionary.getConcept(Dictionary.MARRIED_MONOGAMOUS));
		maritalStatusOptions.add(Dictionary.getConcept(Dictionary.DIVORCED));
		maritalStatusOptions.add(Dictionary.getConcept(Dictionary.WIDOWED));
		maritalStatusOptions.add(Dictionary.getConcept(Dictionary.LIVING_WITH_PARTNER));
		maritalStatusOptions.add(Dictionary.getConcept(Dictionary.NEVER_MARRIED));
		model.addAttribute("maritalStatusOptions", maritalStatusOptions);

		// Create a list of cause of death answer concepts
		List<Concept> causeOfDeathOptions = new ArrayList<Concept>();
		causeOfDeathOptions.add(Dictionary.getConcept(Dictionary.UNKNOWN));
		model.addAttribute("causeOfDeathOptions", causeOfDeathOptions);
	}

	/**
	 * Saves the patient being edited by this form
	 * @param form the edit patient form
	 * @param ui the UI utils
	 * @return a simple object { patientId }
	 */
	public SimpleObject savePatient(@MethodParam("newEditPatientForm") @BindParams EditPatientForm form, UiUtils ui) {
		ui.validate(form, form, null);

		Patient patient = form.save();

		return SimpleObject.create("id", patient.getId());
	}

	/**
	 * Creates an edit patient form
	 * @param person the person
	 * @return the form
	 */
	public EditPatientForm newEditPatientForm(@RequestParam(value = "personId", required = false) Person person) {
		if (person != null && person.isPatient()) {
			return new EditPatientForm((Patient) person); // For editing existing patient
		} else if (person != null) {
			return new EditPatientForm(person); // For creating patient from existing person
		} else {
			return new EditPatientForm(); // For creating patient and person from scratch
		}
	}

	/**
	 * The form command object for editing patients
	 */
	public class EditPatientForm extends AbstractWebForm {

		private Person original;
		private Location location;
		private PersonName personName;
		private PatientIdentifier nationalIdNumber;
		private PatientIdentifier patientClinicNumber;
		private PatientIdentifier hivIdNumber;
		private Date birthdate;
		private Boolean birthdateEstimated;
		private String gender;
		private PersonAddress personAddress;
		private Concept maritalStatus;
		private Concept occupation;
		private Concept education;
		private Obs savedMaritalStatus;
		private Obs savedOccupation;
		private Obs savedEducation;
		private Boolean dead = false;
		private Date deathDate;

		private String telephoneContact;
		private String nameOfNextOfKin;
		private String nextOfKinRelationship;
		private String nextOfKinContact;
		private String nextOfKinAddress;
		private String subChiefName;

		/**
		 * Creates an edit form for a new patient
		 */
		public EditPatientForm() {
			location = Context.getService(KenyaEmrService.class).getDefaultLocation();

			personName = new PersonName();
			personAddress = new PersonAddress();

			nationalIdNumber = new PatientIdentifier(null, MetadataUtils.getPatientIdentifierType(CommonMetadata._PatientIdentifierType.NATIONAL_ID), location);
			patientClinicNumber = new PatientIdentifier(null, MetadataUtils.getPatientIdentifierType(CommonMetadata._PatientIdentifierType.PATIENT_CLINIC_NUMBER), location);
			hivIdNumber = new PatientIdentifier(null, MetadataUtils.getPatientIdentifierType(HivMetadata._PatientIdentifierType.UNIQUE_PATIENT_NUMBER), location);
		}

		/**
		 * Creates an edit form for an existing patient
		 */
		public EditPatientForm(Person person) {
			this();

			original = person;

			if (person.getPersonName() != null) {
				personName = person.getPersonName();
			} else {
				personName.setPerson(person);
			}

			if (person.getPersonAddress() != null) {
				personAddress = person.getPersonAddress();
			} else {
				personAddress.setPerson(person);
			}

			gender = person.getGender();
			birthdate = person.getBirthdate();
			birthdateEstimated = person.getBirthdateEstimated();
			dead = person.isDead();
			deathDate = person.getDeathDate();

			PersonWrapper wrapper = new PersonWrapper(person);
			telephoneContact = wrapper.getTelephoneContact();
		}

		/**
		 * Creates an edit form for an existing patient
		 */
		public EditPatientForm(Patient patient) {
			this((Person) patient);

			PatientIdentifier id = patient.getPatientIdentifier(MetadataUtils.getPatientIdentifierType(CommonMetadata._PatientIdentifierType.PATIENT_CLINIC_NUMBER));
			if (id != null) {
				patientClinicNumber = id;
			} else {
				patientClinicNumber.setPatient(patient);
			}

			id = patient.getPatientIdentifier(MetadataUtils.getPatientIdentifierType(HivMetadata._PatientIdentifierType.UNIQUE_PATIENT_NUMBER));
			if (id != null) {
				hivIdNumber = id;
			} else {
				hivIdNumber.setPatient(patient);
			}

			id = patient.getPatientIdentifier(MetadataUtils.getPatientIdentifierType(CommonMetadata._PatientIdentifierType.NATIONAL_ID));
			if (id != null) {
				nationalIdNumber = id;
			} else {
				nationalIdNumber.setPatient(patient);
			}

			savedMaritalStatus = getLatestObs(patient, Dictionary.CIVIL_STATUS);
			if (savedMaritalStatus != null) {
				maritalStatus = savedMaritalStatus.getValueCoded();
			}

			savedOccupation = getLatestObs(patient, Dictionary.OCCUPATION);
			if (savedOccupation != null) {
				occupation = savedOccupation.getValueCoded();
			}

			savedEducation = getLatestObs(patient, Dictionary.EDUCATION);
			if (savedEducation != null) {
				education = savedEducation.getValueCoded();
			}

			PatientWrapper wrapper = new PatientWrapper(patient);
			nameOfNextOfKin = wrapper.getNextOfKinName();
			nextOfKinRelationship = wrapper.getNextOfKinRelationship();
			nextOfKinContact = wrapper.getNextOfKinContact();
			nextOfKinAddress = wrapper.getNextOfKinAddress();
			subChiefName = wrapper.getSubChiefName();
		}

		private Obs getLatestObs(Patient patient, String conceptIdentifier) {
			Concept concept = Dictionary.getConcept(conceptIdentifier);
			List<Obs> obs = Context.getObsService().getObservationsByPersonAndConcept(patient, concept);
			if (obs.size() > 0) {
				// these are in reverse chronological order
				return obs.get(0);
			}
			return null;
		}

		/**
		 * @see org.springframework.validation.Validator#validate(java.lang.Object,
		 *      org.springframework.validation.Errors)
		 */
		@Override
		public void validate(Object target, Errors errors) {
			require(errors, "personName.givenName");
			require(errors, "personName.familyName");
			require(errors, "gender");
			require(errors, "birthdate");

			// Require death details if patient is deceased
			if (dead) {
				require(errors, "deathDate");

				if (deathDate != null) {
					if (birthdate != null && deathDate.before(birthdate)) {
						errors.rejectValue("deathDate", "Cannot be before birth date");
					}
					if (deathDate.after(new Date())) {
						errors.rejectValue("deathDate", "Cannot be in the future");
					}
				}
			} else if (deathDate != null) {
				errors.rejectValue("deathDate", "Must be empty if patient not deceased");
			}

			if (StringUtils.isBlank(hivIdNumber.getIdentifier())) {
				hivIdNumber = null;
			}
			if (StringUtils.isBlank(nationalIdNumber.getIdentifier())) {
				nationalIdNumber = null;
			}
			if (StringUtils.isBlank(patientClinicNumber.getIdentifier())) {
				patientClinicNumber = null;
			}

			if (StringUtils.isNotBlank(telephoneContact)) {
				validateField(errors, "telephoneContact", new TelephoneNumberValidator());
			}
			if (StringUtils.isNotBlank(nextOfKinContact)) {
				validateField(errors, "nextOfKinContact", new TelephoneNumberValidator());
			}

			validateField(errors, "personAddress");
			validateField(errors, "hivIdNumber");
			validateField(errors, "nationalIdNumber");
			validateField(errors, "patientClinicNumber");

			// check birth date against future dates and really old dates
			if (birthdate != null) {
				if (birthdate.after(new Date()))
					errors.rejectValue("birthdate", "error.date.future");
				else {
					Calendar c = Calendar.getInstance();
					c.setTime(new Date());
					c.add(Calendar.YEAR, -120); // person cannot be older than 120 years old
					if (birthdate.before(c.getTime())) {
						errors.rejectValue("birthdate", "error.date.nonsensical");
					}
				}
			}
		}

		/**
		 * @see org.openmrs.module.kenyaui.form.AbstractWebForm#save()
		 */
		@Override
		public Patient save() {
			Patient toSave;

			if (original != null && original.isPatient()) { // Editing an existing patient
				toSave = (Patient) original;
			}
			else if (original != null) {
				toSave = new Patient(original); // Creating a patient from an existing person
			}
			else {
				toSave = new Patient(); // Creating a new patient and person
			}

			toSave.setGender(gender);
			toSave.setBirthdate(birthdate);
			toSave.setBirthdateEstimated(birthdateEstimated);
			toSave.setDead(dead);
			toSave.setDeathDate(deathDate);
			toSave.setCauseOfDeath(dead ? Dictionary.getConcept(CAUSE_OF_DEATH_PLACEHOLDER) : null);

			PatientIdentifier oldHivId = toSave.getPatientIdentifier(MetadataUtils.getPatientIdentifierType(HivMetadata._PatientIdentifierType.UNIQUE_PATIENT_NUMBER));
			if (anyChanges(oldHivId, hivIdNumber, "identifier")) {
				if (oldHivId != null) {
					voidData(oldHivId);
				}
				toSave.addIdentifier(hivIdNumber);
			}

			PatientIdentifier oldNationalId = toSave.getPatientIdentifier(MetadataUtils.getPatientIdentifierType(CommonMetadata._PatientIdentifierType.NATIONAL_ID));
			if (anyChanges(oldNationalId, nationalIdNumber, "identifier")) {
				if (oldNationalId != null) {
					voidData(oldNationalId);
				}
				toSave.addIdentifier(nationalIdNumber);
			}

			PatientIdentifier oldPatientClinicNumber = toSave.getPatientIdentifier(MetadataUtils.getPatientIdentifierType(CommonMetadata._PatientIdentifierType.PATIENT_CLINIC_NUMBER));
			if (anyChanges(oldPatientClinicNumber, patientClinicNumber, "identifier")) {
				if (oldPatientClinicNumber != null) {
					voidData(oldPatientClinicNumber);
				}
				toSave.addIdentifier(patientClinicNumber);
			}

			{ // make sure everyone gets an OpenMRS ID
				PatientIdentifierType openmrsIdType = MetadataUtils.getPatientIdentifierType(CommonMetadata._PatientIdentifierType.OPENMRS_ID);
				if (toSave.getPatientIdentifier(openmrsIdType) == null) {
					String generated = Context.getService(IdentifierSourceService.class).generateIdentifier(openmrsIdType, "Registration Create/Edit Patient");
					PatientIdentifier generatedOpenmrsId = new PatientIdentifier(generated, openmrsIdType, location);
					toSave.addIdentifier(generatedOpenmrsId);
				}
			}

			if (!toSave.getPatientIdentifier().isPreferred()) {
				toSave.getPatientIdentifier().setPreferred(true);
			}

			if (anyChanges(toSave.getPersonName(), personName, "givenName", "familyName")) {
				if (toSave.getPersonName() != null) {
					voidData(toSave.getPersonName());
				}
				toSave.addName(personName);
			}

			if (anyChanges(toSave.getPersonAddress(), personAddress, "address1", "address2", "address5", "address6", "countyDistrict","address3","cityVillage","stateProvince","country","postalCode","address4")) {
				if (toSave.getPersonAddress() != null) {
					voidData(toSave.getPersonAddress());
				}
				toSave.addAddress(personAddress);
			}

			PatientWrapper wrapper = new PatientWrapper(toSave);
			wrapper.getPerson().setTelephoneContact(telephoneContact);
			wrapper.setNextOfKinName(nameOfNextOfKin);
			wrapper.setNextOfKinRelationship(nextOfKinRelationship);
			wrapper.setNextOfKinContact(nextOfKinContact);
			wrapper.setNextOfKinAddress(nextOfKinAddress);
			wrapper.setSubChiefName(subChiefName);

			Patient ret = Context.getPatientService().savePatient(toSave);

			// Explicitly save all identifiers
			for (PatientIdentifier identifier : ret.getIdentifiers()) {
				Context.getPatientService().savePatientIdentifier(identifier);
			}

			List<Obs> obsToSave = new ArrayList<Obs>();
			List<Obs> obsToVoid = new ArrayList<Obs>();

			handleOncePerPatientObs(ret, obsToSave, obsToVoid, Dictionary.getConcept(Dictionary.CIVIL_STATUS), savedMaritalStatus, maritalStatus);
			handleOncePerPatientObs(ret, obsToSave, obsToVoid, Dictionary.getConcept(Dictionary.OCCUPATION), savedOccupation, occupation);
			handleOncePerPatientObs(ret, obsToSave, obsToVoid, Dictionary.getConcept(Dictionary.EDUCATION), savedEducation, education);

			for (Obs o : obsToVoid) {
				Context.getObsService().voidObs(o, "KenyaEMR edit patient");
			}

			for (Obs o : obsToSave) {
				Context.getObsService().saveObs(o, "KenyaEMR edit patient");
			}

			return ret;
		}

		/**
		 * Handles saving a field which is stored as an obs
		 * @param patient the patient being saved
		 * @param obsToSave
		 * @param obsToVoid
		 * @param question
		 * @param savedObs
		 * @param newValue
		 */
		protected void handleOncePerPatientObs(Patient patient, List<Obs> obsToSave, List<Obs> obsToVoid, Concept question,
											 Obs savedObs, Concept newValue) {
			if (!OpenmrsUtil.nullSafeEquals(savedObs != null ? savedObs.getValueCoded() : null, newValue)) {
				// there was a change
				if (savedObs != null && newValue == null) {
					// treat going from a value to null as voiding all past civil status obs
					obsToVoid.addAll(Context.getObsService().getObservationsByPersonAndConcept(patient, question));
				}
				if (newValue != null) {
					Obs o = new Obs();
					o.setPerson(patient);
					o.setConcept(question);
					o.setObsDatetime(new Date());
					o.setLocation(Context.getService(KenyaEmrService.class).getDefaultLocation());
					o.setValueCoded(newValue);
					obsToSave.add(o);
				}
			}
		}

		public boolean isInHivProgram() {
			if (original == null || !original.isPatient()) {
				return false;
			}
			ProgramWorkflowService pws = Context.getProgramWorkflowService();
			Program hivProgram = MetadataUtils.getProgram(HivMetadata._Program.HIV);
			for (PatientProgram pp : pws.getPatientPrograms((Patient) original, hivProgram, null, null, null, null, false)) {
				if (pp.getActive()) {
					return true;
				}
			}
			return false;
		}

		/**
		 * @return the original
		 */
		public Person getOriginal() {
			return original;
		}

		/**
		 * @param original the original to set
		 */
		public void setOriginal(Patient original) {
			this.original = original;
		}

		/**
		 * @return the personName
		 */
		public PersonName getPersonName() {
			return personName;
		}

		/**
		 * @param personName the personName to set
		 */
		public void setPersonName(PersonName personName) {
			this.personName = personName;
		}

		/**
		 * @return the patientClinicNumber
		 */
		public PatientIdentifier getPatientClinicNumber() {
			return patientClinicNumber;
		}

		/**
		 * @param patientClinicNumber the patientClinicNumber to set
		 */
		public void setPatientClinicNumber(PatientIdentifier patientClinicNumber) {
			this.patientClinicNumber = patientClinicNumber;
		}

		/**
		 * @return the hivIdNumber
		 */
		public PatientIdentifier getHivIdNumber() {
			return hivIdNumber;
		}

		/**
		 * @param hivIdNumber the hivIdNumber to set
		 */
		public void setHivIdNumber(PatientIdentifier hivIdNumber) {
			this.hivIdNumber = hivIdNumber;
		}

		/**
		 * @return the nationalIdNumber
		 */
		public PatientIdentifier getNationalIdNumber() {
			return nationalIdNumber;
		}

		/**
		 * @param nationalIdNumber the nationalIdNumber to set
		 */
		public void setNationalIdNumber(PatientIdentifier nationalIdNumber) {

			this.nationalIdNumber = nationalIdNumber;
		}

		/**
		 * @return the birthdate
		 */
		public Date getBirthdate() {
			return birthdate;
		}

		/**
		 * @param birthdate the birthdate to set
		 */
		public void setBirthdate(Date birthdate) {
			this.birthdate = birthdate;
		}

		/**
		 * @return the birthdateEstimated
		 */
		public Boolean getBirthdateEstimated() {
			return birthdateEstimated;
		}

		/**
		 * @param birthdateEstimated the birthdateEstimated to set
		 */
		public void setBirthdateEstimated(Boolean birthdateEstimated) {
			this.birthdateEstimated = birthdateEstimated;
		}

		/**
		 * @return the gender
		 */
		public String getGender() {
			return gender;
		}

		/**
		 * @param gender the gender to set
		 */
		public void setGender(String gender) {
			this.gender = gender;
		}

		/**
		 * @return the personAddress
		 */
		public PersonAddress getPersonAddress() {
			return personAddress;
		}

		/**
		 * @param personAddress the personAddress to set
		 */
		public void setPersonAddress(PersonAddress personAddress) {
			this.personAddress = personAddress;
		}

		/**
		 * @return the maritalStatus
		 */
		public Concept getMaritalStatus() {
			return maritalStatus;
		}

		/**
		 * @param maritalStatus the maritalStatus to set
		 */
		public void setMaritalStatus(Concept maritalStatus) {
			this.maritalStatus = maritalStatus;
		}

		/**
		 * @return the education
		 */
		public Concept getEducation() {
			return education;
		}

		/**
		 * @param education the education to set
		 */
		public void setEducation(Concept education) {
			this.education = education;
		}

		/**
		 * @return the occupation
		 */
		public Concept getOccupation() {
			return occupation;
		}

		/**
		 * @param occupation the occupation to set
		 */
		public void setOccupation(Concept occupation) {
			this.occupation = occupation;
		}

		/**
		 * @return the telephoneContact
		 */
		public String getTelephoneContact() {
			return telephoneContact;
		}

		/**
		 * @param telephoneContact the telephoneContact to set
		 */
		public void setTelephoneContact(String telephoneContact) {
			this.telephoneContact = telephoneContact;
		}

		public Boolean getDead() {
			return dead;
		}

		public void setDead(Boolean dead) {
			this.dead = dead;
		}

		public Date getDeathDate() {
			return deathDate;
		}

		public void setDeathDate(Date deathDate) {
			this.deathDate = deathDate;
		}

		/**
		 * @return the nameOfNextOfKin
		 */
		public String getNameOfNextOfKin() {
			return nameOfNextOfKin;
		}

		/**
		 * @param nameOfNextOfKin the nameOfNextOfKin to set
		 */
		public void setNameOfNextOfKin(String nameOfNextOfKin) {
			this.nameOfNextOfKin = nameOfNextOfKin;
		}

		/**
		 * @return the nextOfKinRelationship
		 */
		public String getNextOfKinRelationship() {
			return nextOfKinRelationship;
		}

		/**
		 * @param nextOfKinRelationship the nextOfKinRelationship to set
		 */
		public void setNextOfKinRelationship(String nextOfKinRelationship) {
			this.nextOfKinRelationship = nextOfKinRelationship;
		}

		/**
		 * @return the nextOfKinContact
		 */
		public String getNextOfKinContact() {
			return nextOfKinContact;
		}

		/**
		 * @param nextOfKinContact the nextOfKinContact to set
		 */
		public void setNextOfKinContact(String nextOfKinContact) {
			this.nextOfKinContact = nextOfKinContact;
		}

		/**
		 * @return the nextOfKinAddress
		 */
		public String getNextOfKinAddress() {
			return nextOfKinAddress;
		}

		/**
		 * @param nextOfKinAddress the nextOfKinAddress to set
		 */
		public void setNextOfKinAddress(String nextOfKinAddress) {
			this.nextOfKinAddress = nextOfKinAddress;
		}

		/**
		 * @return the subChiefName
		 */
		public String getSubChiefName() {
			return subChiefName;
		}

		/**
		 * @param subChiefName the subChiefName to set
		 */
		public void setSubChiefName(String subChiefName) {
			this.subChiefName = subChiefName;
		}
	}
}