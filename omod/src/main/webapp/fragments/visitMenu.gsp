<div class="ke-panelbar" style="text-align: right">
	<% if (visit) {
		if (config.allowCheckOut) {
	%>
		<%= ui.includeFragment("kenyaui", "widget/dialogForm", [
				buttonConfig: [ label: "Check out of visit", iconProvider: "kenyaui", icon: "buttons/visit_end.png" ],
				dialogConfig: [ heading: "Check Out", width: 50, height: 30 ],
				fields: [
						[ hiddenInputName: "visitId", value: visit.visitId ],
						[ hiddenInputName: "appId", value: currentApp.id ],
						[ label: "End Date and Time", formFieldName: "stopDatetime", class: java.util.Date, initialValue: new Date(), showTime: true ]
				],
				fragmentProvider: "kenyaemr",
				fragment: "registrationUtil",
				action: "stopVisit",
				onSuccessCallback: "ui.navigate('kenyaemr', 'registration/registrationViewPatient', { patientId: " + patient.id + " });",
				submitLabel: ui.message("general.submit"),
				cancelLabel: ui.message("general.cancel")
		]) %>
	<%
		} else {
	%>
		${ ui.includeFragment("kenyaui", "widget/button", [
				iconProvider: "kenyaui",
				icon: "buttons/visit_end.png",
				label: "Check-out at registration",
				href: ui.pageLink("kenyaemr", "registration/registrationViewPatient", [ patientId: patient.id ])
		]) }
	<%
		}
	} else {
		if (config.allowCheckIn) {
	%>
	<%= ui.includeFragment("kenyaui", "widget/dialogForm", [
			buttonConfig: [ label: "Check in for visit", iconProvider: "kenyaui", icon: "buttons/registration.png" ],
			dialogConfig: [ heading: "Check In", width: 50, height: 30 ],
			prefix: "visit",
			commandObject: newCurrentVisit,
			hiddenProperties: [ "patient" ],
			properties: [ "visitType", "startDatetime" ],
			propConfig: [
					"visitType": [ type: "radio" ],
			],
			fieldConfig: [
					"visitType": [ label: "Visit Type" ],
					"startDatetime": [ showTime: true ]
			],
			fragmentProvider: "kenyaemr",
			fragment: "registrationUtil",
			action: "startVisit",
			onSuccessCallback: "ui.navigate('kenyaemr', 'registration/registrationViewPatient', { patientId: " + patient.id + " });",
			submitLabel: ui.message("general.submit"),
			cancelLabel: ui.message("general.cancel")
	]) %>
	<% 	} else { %>
		${ ui.includeFragment("kenyaui", "widget/button", [
				iconProvider: "kenyaui",
				icon: "buttons/registration.png",
				label: "Check-in at registration",
				href: ui.pageLink("kenyaemr", "registration/registrationViewPatient", [ patientId: patient.id ])
		]) }
	<%
		}
	} %>
</div>