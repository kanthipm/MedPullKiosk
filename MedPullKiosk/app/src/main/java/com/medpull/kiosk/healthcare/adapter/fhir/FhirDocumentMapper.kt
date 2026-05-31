package com.medpull.kiosk.healthcare.adapter.fhir

import com.medpull.kiosk.healthcare.models.*
import org.hl7.fhir.r4.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps between HealthcareDocumentReference and FHIR R4 DocumentReference resource.
 */
@Singleton
class FhirDocumentMapper @Inject constructor() {

    fun toFhir(doc: HealthcareDocumentReference): DocumentReference {
        return DocumentReference().apply {
            if (doc.id != null) {
                id = doc.id
            }
            status = when (doc.status) {
                DocumentStatus.CURRENT -> Enumerations.DocumentReferenceStatus.CURRENT
                DocumentStatus.SUPERSEDED -> Enumerations.DocumentReferenceStatus.SUPERSEDED
                DocumentStatus.ENTERED_IN_ERROR -> Enumerations.DocumentReferenceStatus.ENTEREDINERROR
            }
            if (doc.type != null) {
                type = CodeableConcept().addCoding(Coding().apply {
                    system = doc.type.system
                    code = doc.type.code
                    display = doc.type.display
                })
            }
            if (doc.subjectId != null) {
                subject = Reference("Patient/${doc.subjectId}")
            }
            if (doc.date != null) {
                dateElement = InstantType(doc.date)
            }
            description = doc.description

            doc.content.forEach { attachment ->
                addContent(DocumentReference.DocumentReferenceContentComponent().apply {
                    this.attachment = Attachment().apply {
                        contentType = attachment.contentType
                        if (attachment.data != null) {
                            data = attachment.data
                        }
                        if (attachment.url != null) {
                            url = attachment.url
                        }
                        title = attachment.title
                        if (attachment.size != null) {
                            size = attachment.size.toInt()
                        }
                    }
                })
            }
        }
    }

    fun fromFhir(fhirDoc: DocumentReference): HealthcareDocumentReference {
        return HealthcareDocumentReference(
            id = fhirDoc.idElement?.idPart,
            status = when (fhirDoc.status) {
                Enumerations.DocumentReferenceStatus.CURRENT -> DocumentStatus.CURRENT
                Enumerations.DocumentReferenceStatus.SUPERSEDED -> DocumentStatus.SUPERSEDED
                Enumerations.DocumentReferenceStatus.ENTEREDINERROR -> DocumentStatus.ENTERED_IN_ERROR
                else -> DocumentStatus.CURRENT
            },
            type = fhirDoc.type?.codingFirstRep?.let { coding ->
                HealthcareCoding(
                    system = coding.system,
                    code = coding.code ?: "",
                    display = coding.display
                )
            },
            subjectId = fhirDoc.subject?.referenceElement?.idPart,
            date = fhirDoc.dateElement?.valueAsString,
            description = fhirDoc.description,
            content = fhirDoc.content?.map { content ->
                val att = content.attachment
                HealthcareAttachment(
                    contentType = att.contentType ?: "application/octet-stream",
                    data = att.data,
                    url = att.url,
                    title = att.title,
                    size = att.size.toLong()
                )
            } ?: emptyList()
        )
    }
}
