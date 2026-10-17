package fr.jarvis.fixture.p2

internal object RecoveryTables {
 val fields=mapOf(
  "DISCOVER" to setOf("contract","kind","nonce","pilot_id","round_id","session_id","version"),
  "NEGOTIATED" to setOf("broker_epoch","code","contract","core_process_epoch","endpoint_epoch","kind","llm_process_epoch","llm_service_epoch","nonce","pilot_id","round_id","session_id","version"),
  "CHALLENGE" to setOf("broker_epoch","contract","core_cert_sha256","core_journal_id","core_journal_seq","core_journal_sha256","core_journal_state","core_process_epoch","core_uid","endpoint_epoch","expires_elapsed_ms","generation","issued_elapsed_ms","kind","llm_cert_sha256","llm_journal_id","llm_journal_seq","llm_journal_sha256","llm_journal_state","llm_process_epoch","llm_service_epoch","llm_uid","nonce","owner","participant_set_digest","pilot_id","prior_key_state","recovery_id","round_id","session_id","version"),
  "ATTEST" to setOf("active_imports","active_native_calls","backend_count","backend_registry_state","barrier_id","broker_epoch","contract","core_cert_sha256","core_journal_id","core_journal_seq","core_journal_sha256","core_journal_state","core_process_epoch","core_uid","endpoint_epoch","expires_elapsed_ms","generation","heavy_leases","issued_elapsed_ms","kind","lease_state","llm_cert_sha256","llm_journal_id","llm_journal_seq","llm_journal_sha256","llm_journal_state","llm_process_epoch","llm_service_epoch","llm_uid","native_state","nonce","observation_seq","owner","participant","participant_set_digest","pilot_id","predecessor_journal_sha256","prepared_journal_seq","prepared_journal_sha256","prior_key_state","queue_state","queued_operations","recovery_id","residency_state","resident_models","resolution_kind","round_id","session_id","terminal_receipt_sha256","version"),
  "COMMIT" to setOf("broker_epoch","contract","core_cert_sha256","core_journal_id","core_journal_seq","core_journal_sha256","core_journal_state","core_process_epoch","core_uid","decision_id","decision_sha256","endpoint_epoch","expires_elapsed_ms","generation","issued_elapsed_ms","kind","llm_cert_sha256","llm_journal_id","llm_journal_seq","llm_journal_sha256","llm_journal_state","llm_process_epoch","llm_service_epoch","llm_uid","nonce","owner","participant_set_digest","pilot_id","prepare_set_sha256","prior_key_state","recovery_id","round_id","session_id","version"),
  "COMMIT_ACK" to setOf("broker_epoch","committed_journal_seq","committed_journal_sha256","contract","core_cert_sha256","core_journal_id","core_journal_seq","core_journal_sha256","core_journal_state","core_process_epoch","core_uid","decision_id","decision_sha256","endpoint_epoch","expires_elapsed_ms","generation","issued_elapsed_ms","kind","llm_cert_sha256","llm_journal_id","llm_journal_seq","llm_journal_sha256","llm_journal_state","llm_process_epoch","llm_recovery_state","llm_service_epoch","llm_uid","nonce","owner","participant_set_digest","pilot_id","prepare_set_sha256","prior_key_state","recovery_id","round_id","session_id","version"),
  "INSPECT" to setOf("broker_epoch","contract","core_cert_sha256","core_process_epoch","core_uid","endpoint_epoch","expires_elapsed_ms","issued_elapsed_ms","kind","llm_cert_sha256","llm_process_epoch","llm_service_epoch","llm_uid","nonce","participant_set_digest","pilot_id","prior_key_state","recovery_id","round_id","session_id","version"),
  "SNAPSHOT" to setOf("active_imports","active_native_calls","backend_count","backend_registry_state","barrier_id","broker_epoch","code","contract","core_cert_sha256","core_journal_id","core_journal_seq","core_journal_sha256","core_journal_state","core_process_epoch","core_uid","endpoint_epoch","expires_elapsed_ms","generation","heavy_leases","issued_elapsed_ms","kind","lease_state","llm_cert_sha256","llm_journal_id","llm_journal_seq","llm_journal_sha256","llm_journal_state","llm_process_epoch","llm_service_epoch","llm_uid","native_state","nonce","observation_seq","owner","participant","participant_set_digest","pilot_id","prior_key_state","queue_state","queued_operations","recovery_id","residency_state","resident_models","resolution_kind","round_id","session_id","terminal_receipt_sha256","version"),
  "CLEANUP_REQUEST" to setOf("broker_epoch","cleanup_command_id","contract","core_cert_sha256","core_process_epoch","core_uid","endpoint_epoch","expires_elapsed_ms","issued_elapsed_ms","kind","llm_cert_sha256","llm_process_epoch","llm_service_epoch","llm_uid","nonce","participant_set_digest","pilot_id","prior_key_state","recovery_id","round_id","session_id","version"),
  "REFUSED" to setOf("code","contract","kind","nonce","round_id","version")
 )
 val prior=setOf("prior_authorization_ref","prior_cancellation_id","prior_correlation_id","prior_epoch","prior_generation","prior_lease_id","prior_operation_id","prior_request_digest","prior_session_id")
 val enums=mapOf(
  "owner" to setOf("NONE","llm"),
  "prior_key_state" to setOf("NONE","PRESENT"),
  "participant" to setOf("core","llm"),
  "backend_registry_state" to setOf("COMPLETE","UNKNOWN"),
  "queue_state" to setOf("EMPTY","BUSY","UNKNOWN"),
  "native_state" to setOf("NOT_STARTED","NATIVE_RETURNED","IN_PROGRESS","UNKNOWN"),
  "residency_state" to setOf("NO_RESIDENCY_CONFIRMED","RESIDENT","UNKNOWN"),
  "lease_state" to setOf("NONE","HELD","UNKNOWN"),
  "resolution_kind" to setOf("INITIAL","CLEAN_RESTART","TERMINAL_REATTESTED","CLEANUP_RETRY_COMPLETED","UNKNOWN"),
  "llm_recovery_state" to setOf("RECOVERY_BLOCKED","QUIESCENT_PREPARED","RECOVERY_COMMITTED","UNKNOWN","CLEANUP_FAILED")
 )
 val codes=setOf("NONE","INCOMPATIBLE","MALFORMED","INVALID_FIELD","INVALID_NONCE","INVALID_NUMBER","INPUT_TOO_LARGE","STALE_INCARNATION","EXPIRED","REPLAY_CONFLICT","BUSY","QUEUE_FULL","JOURNAL_BLOCKED","JOURNAL_MISSING","ENROLMENT_REQUIRED","ENROLMENT_CONFLICT","RECOVERY_REQUIRED","NOT_QUIESCENT","UNKNOWN","CLEANUP_FAILED","CLEANUP_UNAVAILABLE","PRECONDITION_CHANGED","DECISION_MISMATCH","UNAUTHENTICATED","PEER_INCARNATION_REQUIRED","PEER_JOURNAL_REQUIRED")
}
