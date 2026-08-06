package com.nova.runtime.android.contactsAdapter

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.android.internal.PermissionChecker
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsAdapterImpl(
    private val context: Context,
    private val logger: NovaLogger,
) : ContactsAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            ContactsOperations.SEARCH,
            ContactsOperations.RETRIEVE,
            ContactsOperations.CREATE,
            ContactsOperations.UPDATE,
        )

    override fun requiredPermissions(): Set<String> =
        setOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
        )

    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        if (operation !in supportedOperations()) {
            return CapabilityResult.Failure(
                AdapterErrorMapper.invalidOperation(ADAPTER_NAME, operation, supportedOperations()),
            )
        }
        return AdapterBoundary.execute(logger, ADAPTER_NAME, operation, traceId) {
            withContext(Dispatchers.IO) {
                when (operation) {
                    ContactsOperations.SEARCH -> search(parameters)
                    ContactsOperations.RETRIEVE -> retrieve(parameters)
                    ContactsOperations.CREATE -> create(parameters)
                    ContactsOperations.UPDATE -> update(parameters)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun search(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.READ_CONTACTS)
        val query = parameters["query"].orEmpty()
        val selection = if (query.isBlank()) null else "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = if (query.isBlank()) null else arrayOf("%$query%")
        val results = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            selection,
            selectionArgs,
            ContactsContract.Contacts.DISPLAY_NAME,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                results.add("${cursor.getLong(idIndex)}:${cursor.getString(nameIndex)}")
            }
        } ?: throw IllegalStateException("Contacts provider unavailable")
        return mapOf("count" to results.size.toString(), "contacts" to results.joinToString("|"))
    }

    private fun retrieve(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.READ_CONTACTS)
        val contactId = parameters.require("contactId").toLong()
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) throw IllegalArgumentException("Contact not found: $contactId")
            val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
            val phone = readPrimaryPhone(contactId)
            return mapOf(
                "contactId" to contactId.toString(),
                "displayName" to (name ?: ""),
                "phoneNumber" to (phone ?: ""),
            )
        } ?: throw IllegalStateException("Contacts provider unavailable")
    }

    private fun readPrimaryPhone(contactId: Long): String? {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }
        return null
    }

    private fun create(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.WRITE_CONTACTS)
        val displayName = parameters.require("displayName")
        val phoneNumber = parameters["phoneNumber"]
        val operations = ArrayList<ContentProviderOperation>()
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
        )
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .build(),
        )
        phoneNumber?.let { phone ->
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    )
                    .build(),
            )
        }
        val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        val rawContactUri = results.firstOrNull()?.uri
            ?: throw IllegalStateException("Failed to create contact")
        val rawContactId = ContentUris.parseId(rawContactUri)
        return mapOf("rawContactId" to rawContactId.toString(), "displayName" to displayName)
    }

    private fun update(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.WRITE_CONTACTS)
        val contactId = parameters.require("contactId").toLong()
        val displayName = parameters["displayName"]
        if (displayName != null) {
            val ops =
                arrayListOf(
                    ContentProviderOperation.newUpdate(
                        ContactsContract.Data.CONTENT_URI,
                    )
                        .withSelection(
                            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(
                                contactId.toString(),
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                            ),
                        )
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                        .build(),
                )
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }
        return mapOf("contactId" to contactId.toString(), "status" to "updated")
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")

    private companion object {
        const val ADAPTER_NAME = "Contacts"
    }
}

class ContactsAdapterStub : ContactsAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> =
        setOf(
            ContactsOperations.SEARCH,
            ContactsOperations.RETRIEVE,
            ContactsOperations.CREATE,
            ContactsOperations.UPDATE,
        )

    override fun requiredPermissions(): Set<String> = emptySet()
}
