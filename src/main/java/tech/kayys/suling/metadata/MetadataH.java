package tech.kayys.suling.metadata;

import tech.kayys.suling.internal.FlacLibrary;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * Raw FFM downcall handles for the three libFLAC metadata interfaces:
 * <ul>
 *   <li><b>Level 0</b> – read-only, fast, single-pass ({@code FLAC__metadata_get_*})</li>
 *   <li><b>Level 1</b> – iterator-based read/write ({@code FLAC__metadata_simple_iterator_*})</li>
 *   <li><b>Level 2</b> – full in-memory chain ({@code FLAC__metadata_chain_*} /
 *       {@code FLAC__metadata_iterator_*})</li>
 * </ul>
 * Prefer {@link FlacMetadata} for a safer Java wrapper.
 */
public final class MetadataH {

    private MetadataH() {}

    // -----------------------------------------------------------------------
    // FLAC__Metadata_SimpleIteratorStatus enum
    // -----------------------------------------------------------------------
    public static final int SIMPLE_ITER_STATUS_OK                         = 0;
    public static final int SIMPLE_ITER_STATUS_ILLEGAL_INPUT              = 1;
    public static final int SIMPLE_ITER_STATUS_ERROR_OPENING_FILE         = 2;
    public static final int SIMPLE_ITER_STATUS_NOT_A_FLAC_FILE            = 3;
    public static final int SIMPLE_ITER_STATUS_NOT_WRITABLE               = 4;
    public static final int SIMPLE_ITER_STATUS_BAD_METADATA               = 5;
    public static final int SIMPLE_ITER_STATUS_READ_ERROR                 = 6;
    public static final int SIMPLE_ITER_STATUS_SEEK_ERROR                 = 7;
    public static final int SIMPLE_ITER_STATUS_WRITE_ERROR                = 8;
    public static final int SIMPLE_ITER_STATUS_RENAME_ERROR               = 9;
    public static final int SIMPLE_ITER_STATUS_UNLINK_ERROR               = 10;
    public static final int SIMPLE_ITER_STATUS_MEMORY_ALLOCATION_ERROR    = 11;
    public static final int SIMPLE_ITER_STATUS_INTERNAL_ERROR             = 12;

    // -----------------------------------------------------------------------
    // FLAC__Metadata_ChainStatus enum
    // -----------------------------------------------------------------------
    public static final int CHAIN_STATUS_OK                               = 0;
    public static final int CHAIN_STATUS_ILLEGAL_INPUT                    = 1;
    public static final int CHAIN_STATUS_ERROR_OPENING_FILE               = 2;
    public static final int CHAIN_STATUS_NOT_A_FLAC_FILE                  = 3;
    public static final int CHAIN_STATUS_NOT_WRITABLE                     = 4;
    public static final int CHAIN_STATUS_BAD_METADATA                     = 5;
    public static final int CHAIN_STATUS_READ_ERROR                       = 6;
    public static final int CHAIN_STATUS_SEEK_ERROR                       = 7;
    public static final int CHAIN_STATUS_WRITE_ERROR                      = 8;
    public static final int CHAIN_STATUS_RENAME_ERROR                     = 9;
    public static final int CHAIN_STATUS_UNLINK_ERROR                     = 10;
    public static final int CHAIN_STATUS_MEMORY_ALLOCATION_ERROR          = 11;
    public static final int CHAIN_STATUS_INTERNAL_ERROR                   = 12;
    public static final int CHAIN_STATUS_INVALID_CALLBACKS                = 13;
    public static final int CHAIN_STATUS_READ_WRITE_MISMATCH              = 14;
    public static final int CHAIN_STATUS_WRONG_WRITE_CALL                 = 15;

    // -----------------------------------------------------------------------
    // Level 0 – FLAC__metadata_get_*
    // -----------------------------------------------------------------------

    // FLAC__bool FLAC__metadata_get_streaminfo(const char*, FLAC__StreamMetadata*)
    private static final MethodHandle MH_get_streaminfo = FlacLibrary.downcall(
            "FLAC__metadata_get_streaminfo",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__metadata_get_tags(const char*, FLAC__StreamMetadata**)
    private static final MethodHandle MH_get_tags = FlacLibrary.downcall(
            "FLAC__metadata_get_tags",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__metadata_get_cuesheet(const char*, FLAC__StreamMetadata**)
    private static final MethodHandle MH_get_cuesheet = FlacLibrary.downcall(
            "FLAC__metadata_get_cuesheet",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__metadata_get_picture(const char*, FLAC__StreamMetadata**, PictureType,
    //            const char* mime_type, const FLAC__byte* description,
    //            uint32_t max_width, uint32_t max_height,
    //            uint32_t max_depth, uint32_t max_colors)
    private static final MethodHandle MH_get_picture = FlacLibrary.downcall(
            "FLAC__metadata_get_picture",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, ADDRESS, JAVA_INT,
                    ADDRESS, ADDRESS,
                    JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

    // -----------------------------------------------------------------------
    // FLAC__StreamMetadata object lifecycle
    // -----------------------------------------------------------------------

    // FLAC__StreamMetadata *FLAC__metadata_object_new(FLAC__MetadataType)
    private static final MethodHandle MH_object_new = FlacLibrary.downcall(
            "FLAC__metadata_object_new",
            FunctionDescriptor.of(ADDRESS, JAVA_INT));

    // FLAC__StreamMetadata *FLAC__metadata_object_clone(const FLAC__StreamMetadata*)
    private static final MethodHandle MH_object_clone = FlacLibrary.downcall(
            "FLAC__metadata_object_clone",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    // void FLAC__metadata_object_delete(FLAC__StreamMetadata*)
    private static final MethodHandle MH_object_delete = FlacLibrary.downcall(
            "FLAC__metadata_object_delete",
            FunctionDescriptor.ofVoid(ADDRESS));

    // FLAC__bool FLAC__metadata_object_is_equal(const*, const*)
    private static final MethodHandle MH_object_is_equal = FlacLibrary.downcall(
            "FLAC__metadata_object_is_equal",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // -----------------------------------------------------------------------
    // SeekTable helpers
    // -----------------------------------------------------------------------
    // FLAC__bool FLAC__metadata_object_seektable_resize_points(meta, uint32_t)
    private static final MethodHandle MH_seektable_resize = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_resize_points",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // void FLAC__metadata_object_seektable_set_point(meta, uint32_t idx, SeekPoint)
    // Note: SeekPoint is a struct passed by value (24 bytes)
    private static final MethodHandle MH_seektable_set_point = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_set_point",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS)); // pass by ptr in practice

    // FLAC__bool FLAC__metadata_object_seektable_insert_point(meta, uint32_t idx, SeekPoint)
    private static final MethodHandle MH_seektable_insert_point = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_insert_point",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__metadata_object_seektable_delete_point(meta, uint32_t)
    private static final MethodHandle MH_seektable_delete_point = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_delete_point",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_seektable_is_legal(const meta*)
    private static final MethodHandle MH_seektable_is_legal = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_is_legal",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__metadata_object_seektable_template_append_placeholders(meta, uint32_t)
    private static final MethodHandle MH_seektable_tmpl_append_placeholders = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_template_append_placeholders",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_seektable_template_append_point(meta, FLAC__uint64)
    private static final MethodHandle MH_seektable_tmpl_append_point = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_template_append_point",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    // FLAC__bool FLAC__metadata_object_seektable_template_append_points(meta, uint64[], uint32_t)
    private static final MethodHandle MH_seektable_tmpl_append_points = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_template_append_points",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_seektable_template_append_spaced_points(meta, uint32_t, uint64_t)
    private static final MethodHandle MH_seektable_tmpl_spaced = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_template_append_spaced_points",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));

    // FLAC__bool FLAC__metadata_object_seektable_template_sort(meta, FLAC__bool)
    private static final MethodHandle MH_seektable_tmpl_sort = FlacLibrary.downcall(
            "FLAC__metadata_object_seektable_template_sort",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // -----------------------------------------------------------------------
    // VorbisComment helpers
    // -----------------------------------------------------------------------
    // FLAC__bool FLAC__metadata_object_vorbiscomment_set_vendor_string(meta, FLAC__StreamMetadata_VorbisComment_Entry, FLAC__bool)
    // Entry is struct { uint32_t length; FLAC__byte *entry; }
    // We use the string helper instead:

    // FLAC__bool FLAC__metadata_object_vorbiscomment_entry_from_name_value_pair(FLAC__StreamMetadata_VorbisComment_Entry *, const char *, const char *)
    private static final MethodHandle MH_vc_entry_from_name_value = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_entry_from_name_value_pair",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__metadata_object_vorbiscomment_entry_to_name_value_pair(const FLAC__StreamMetadata_VorbisComment_Entry, char **, char **)
    private static final MethodHandle MH_vc_entry_to_name_value = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_entry_to_name_value_pair",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__metadata_object_vorbiscomment_append_comment(meta, Entry, copy)
    private static final MethodHandle MH_vc_append = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_append_comment",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_vorbiscomment_insert_comment(meta, uint32_t, Entry, copy)
    private static final MethodHandle MH_vc_insert = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_insert_comment",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_vorbiscomment_delete_comment(meta, uint32_t)
    private static final MethodHandle MH_vc_delete = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_delete_comment",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // int FLAC__metadata_object_vorbiscomment_find_entry_from(const meta, uint32_t offset, const char* field_name)
    private static final MethodHandle MH_vc_find = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_find_entry_from",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    // int FLAC__metadata_object_vorbiscomment_remove_entry_matching(meta, const char* field_name)
    private static final MethodHandle MH_vc_remove_matching = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_remove_entry_matching",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // int FLAC__metadata_object_vorbiscomment_remove_entries_matching(meta, const char* field_name)
    private static final MethodHandle MH_vc_remove_all_matching = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_remove_entries_matching",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__metadata_object_vorbiscomment_replace_comment(meta, Entry, all, copy)
    private static final MethodHandle MH_vc_replace = FlacLibrary.downcall(
            "FLAC__metadata_object_vorbiscomment_replace_comment",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

    // -----------------------------------------------------------------------
    // Picture helpers
    // -----------------------------------------------------------------------
    // FLAC__bool FLAC__metadata_object_picture_set_mime_type(meta, const char*, copy)
    private static final MethodHandle MH_pic_set_mime = FlacLibrary.downcall(
            "FLAC__metadata_object_picture_set_mime_type",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_picture_set_description(meta, const FLAC__byte*, copy)
    private static final MethodHandle MH_pic_set_desc = FlacLibrary.downcall(
            "FLAC__metadata_object_picture_set_description",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_picture_set_data(meta, FLAC__byte*, uint32_t, copy)
    private static final MethodHandle MH_pic_set_data = FlacLibrary.downcall(
            "FLAC__metadata_object_picture_set_data",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

    // FLAC__bool FLAC__metadata_object_picture_is_legal(const meta*, const char** violation)
    private static final MethodHandle MH_pic_is_legal = FlacLibrary.downcall(
            "FLAC__metadata_object_picture_is_legal",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // -----------------------------------------------------------------------
    // Level 1 – Simple Iterator
    // -----------------------------------------------------------------------

    // FLAC__Metadata_SimpleIterator *FLAC__metadata_simple_iterator_new()
    private static final MethodHandle MH_si_new = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_new",
            FunctionDescriptor.of(ADDRESS));

    // void FLAC__metadata_simple_iterator_delete(iter)
    private static final MethodHandle MH_si_delete = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_delete",
            FunctionDescriptor.ofVoid(ADDRESS));

    // FLAC__Metadata_SimpleIteratorStatus FLAC__metadata_simple_iterator_status(iter)
    private static final MethodHandle MH_si_status = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_status",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__metadata_simple_iterator_init(iter, filename, read_only, preserve_file_stats)
    private static final MethodHandle MH_si_init = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_init",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

    // FLAC__bool FLAC__metadata_simple_iterator_is_writable(iter)
    private static final MethodHandle MH_si_is_writable = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_is_writable",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__metadata_simple_iterator_next(iter)
    private static final MethodHandle MH_si_next = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_next",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__metadata_simple_iterator_prev(iter)
    private static final MethodHandle MH_si_prev = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_prev",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__metadata_simple_iterator_is_last(iter)
    private static final MethodHandle MH_si_is_last = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_is_last",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // off_t FLAC__metadata_simple_iterator_get_block_offset(iter) -> long
    private static final MethodHandle MH_si_get_block_offset = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_get_block_offset",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    // FLAC__MetadataType FLAC__metadata_simple_iterator_get_block_type(iter)
    private static final MethodHandle MH_si_get_block_type = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_get_block_type",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // uint32_t FLAC__metadata_simple_iterator_get_block_length(iter)
    private static final MethodHandle MH_si_get_block_length = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_get_block_length",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__metadata_simple_iterator_get_application_id(iter, FLAC__byte id[4])
    private static final MethodHandle MH_si_get_app_id = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_get_application_id",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__StreamMetadata *FLAC__metadata_simple_iterator_get_block(iter)
    private static final MethodHandle MH_si_get_block = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_get_block",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    // FLAC__bool FLAC__metadata_simple_iterator_set_block(iter, meta, use_padding)
    private static final MethodHandle MH_si_set_block = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_set_block",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_simple_iterator_insert_block_after(iter, meta, use_padding)
    private static final MethodHandle MH_si_insert_after = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_insert_block_after",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__metadata_simple_iterator_delete_block(iter, use_padding)
    private static final MethodHandle MH_si_delete_block = FlacLibrary.downcall(
            "FLAC__metadata_simple_iterator_delete_block",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // -----------------------------------------------------------------------
    // Level 2 – Chain
    // -----------------------------------------------------------------------

    private static final MethodHandle MH_chain_new = FlacLibrary.downcall(
            "FLAC__metadata_chain_new", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle MH_chain_delete = FlacLibrary.downcall(
            "FLAC__metadata_chain_delete", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle MH_chain_status = FlacLibrary.downcall(
            "FLAC__metadata_chain_status", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_chain_read = FlacLibrary.downcall(
            "FLAC__metadata_chain_read", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle MH_chain_read_ogg = FlacLibrary.downcall(
            "FLAC__metadata_chain_read_ogg", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle MH_chain_read_with_callbacks = FlacLibrary.downcall(
            "FLAC__metadata_chain_read_with_callbacks",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)); // chain, client_data, IOCallbacks
    private static final MethodHandle MH_chain_write = FlacLibrary.downcall(
            "FLAC__metadata_chain_write",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT)); // chain, use_padding, preserve_file_stats
    private static final MethodHandle MH_chain_write_with_callbacks = FlacLibrary.downcall(
            "FLAC__metadata_chain_write_with_callbacks",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle MH_chain_write_with_callbacks_and_tempfile = FlacLibrary.downcall(
            "FLAC__metadata_chain_write_with_callbacks_and_tempfile",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle MH_chain_merge_padding = FlacLibrary.downcall(
            "FLAC__metadata_chain_merge_padding", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle MH_chain_sort_padding = FlacLibrary.downcall(
            "FLAC__metadata_chain_sort_padding", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle MH_chain_check_if_tempfile_needed = FlacLibrary.downcall(
            "FLAC__metadata_chain_check_if_tempfile_needed",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // Level 2 – Iterator
    private static final MethodHandle MH_iter_new = FlacLibrary.downcall(
            "FLAC__metadata_iterator_new", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle MH_iter_delete = FlacLibrary.downcall(
            "FLAC__metadata_iterator_delete", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle MH_iter_init = FlacLibrary.downcall(
            "FLAC__metadata_iterator_init", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle MH_iter_next = FlacLibrary.downcall(
            "FLAC__metadata_iterator_next", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_iter_prev = FlacLibrary.downcall(
            "FLAC__metadata_iterator_prev", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_iter_get_block_type = FlacLibrary.downcall(
            "FLAC__metadata_iterator_get_block_type", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_iter_get_block = FlacLibrary.downcall(
            "FLAC__metadata_iterator_get_block", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle MH_iter_set_block = FlacLibrary.downcall(
            "FLAC__metadata_iterator_set_block", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle MH_iter_delete_block = FlacLibrary.downcall(
            "FLAC__metadata_iterator_delete_block", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle MH_iter_insert_before = FlacLibrary.downcall(
            "FLAC__metadata_iterator_insert_block_before", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle MH_iter_insert_after = FlacLibrary.downcall(
            "FLAC__metadata_iterator_insert_block_after", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // -----------------------------------------------------------------------
    // Public static invocation wrappers
    // -----------------------------------------------------------------------

    // -- Level 0 --
    public static boolean metadataGetStreaminfo(MemorySegment filename, MemorySegment out) {
        try { return (int) MH_get_streaminfo.invokeExact(filename, out) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean metadataGetTags(MemorySegment filename, MemorySegment outPtr) {
        try { return (int) MH_get_tags.invokeExact(filename, outPtr) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean metadataGetCuesheet(MemorySegment filename, MemorySegment outPtr) {
        try { return (int) MH_get_cuesheet.invokeExact(filename, outPtr) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean metadataGetPicture(MemorySegment filename, MemorySegment outPtr,
            int type, MemorySegment mimeType, MemorySegment description,
            int maxWidth, int maxHeight, int maxDepth, int maxColors) {
        try { return (int) MH_get_picture.invokeExact(
                filename, outPtr, type, mimeType, description,
                maxWidth, maxHeight, maxDepth, maxColors) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- Object lifecycle --
    public static MemorySegment objectNew(int type) {
        try { return (MemorySegment) MH_object_new.invokeExact(type); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static MemorySegment objectClone(MemorySegment meta) {
        try { return (MemorySegment) MH_object_clone.invokeExact(meta); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static void objectDelete(MemorySegment meta) {
        try { MH_object_delete.invokeExact(meta); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean objectIsEqual(MemorySegment a, MemorySegment b) {
        try { return (int) MH_object_is_equal.invokeExact(a, b) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- SeekTable --
    public static boolean seektableResize(MemorySegment m, int n) {
        try { return (int) MH_seektable_resize.invokeExact(m, n) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean seektableIsLegal(MemorySegment m) {
        try { return (int) MH_seektable_is_legal.invokeExact(m) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean seektableTmplAppendPlaceholders(MemorySegment m, int n) {
        try { return (int) MH_seektable_tmpl_append_placeholders.invokeExact(m, n) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean seektableTmplAppendPoint(MemorySegment m, long sampleNum) {
        try { return (int) MH_seektable_tmpl_append_point.invokeExact(m, sampleNum) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean seektableTmplSpacedPoints(MemorySegment m, int n, long totalSamples) {
        try { return (int) MH_seektable_tmpl_spaced.invokeExact(m, n, totalSamples) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean seektableTmplSort(MemorySegment m, boolean compact) {
        try { return (int) MH_seektable_tmpl_sort.invokeExact(m, compact ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean seektableDeletePoint(MemorySegment m, int idx) {
        try { return (int) MH_seektable_delete_point.invokeExact(m, idx) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- VorbisComment --
    public static boolean vcAppend(MemorySegment m, MemorySegment entryStruct, boolean copy) {
        try { return (int) MH_vc_append.invokeExact(m, entryStruct, copy ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean vcInsert(MemorySegment m, int idx, MemorySegment entryStruct, boolean copy) {
        try { return (int) MH_vc_insert.invokeExact(m, idx, entryStruct, copy ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean vcDelete(MemorySegment m, int idx) {
        try { return (int) MH_vc_delete.invokeExact(m, idx) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int vcFind(MemorySegment m, int offset, MemorySegment fieldName) {
        try { return (int) MH_vc_find.invokeExact(m, offset, fieldName); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int vcRemoveMatching(MemorySegment m, MemorySegment fieldName) {
        try { return (int) MH_vc_remove_matching.invokeExact(m, fieldName); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int vcRemoveAllMatching(MemorySegment m, MemorySegment fieldName) {
        try { return (int) MH_vc_remove_all_matching.invokeExact(m, fieldName); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean vcReplace(MemorySegment m, MemorySegment entry, boolean all, boolean copy) {
        try { return (int) MH_vc_replace.invokeExact(m, entry, all ? 1 : 0, copy ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean vcEntryFromNameValuePair(MemorySegment entryOut, MemorySegment name, MemorySegment value) {
        try { return (int) MH_vc_entry_from_name_value.invokeExact(entryOut, name, value) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- Picture --
    public static boolean picSetMimeType(MemorySegment m, MemorySegment mime, boolean copy) {
        try { return (int) MH_pic_set_mime.invokeExact(m, mime, copy ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean picSetDescription(MemorySegment m, MemorySegment desc, boolean copy) {
        try { return (int) MH_pic_set_desc.invokeExact(m, desc, copy ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean picSetData(MemorySegment m, MemorySegment data, int len, boolean copy) {
        try { return (int) MH_pic_set_data.invokeExact(m, data, len, copy ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean picIsLegal(MemorySegment m, MemorySegment violationOut) {
        try { return (int) MH_pic_is_legal.invokeExact(m, violationOut) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- Simple Iterator --
    public static MemorySegment simpleIteratorNew() {
        try { return (MemorySegment) MH_si_new.invokeExact(); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static void simpleIteratorDelete(MemorySegment it) {
        try { MH_si_delete.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int simpleIteratorStatus(MemorySegment it) {
        try { return (int) MH_si_status.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorInit(MemorySegment it, MemorySegment filename, boolean readOnly, boolean preserveStats) {
        try { return (int) MH_si_init.invokeExact(it, filename, readOnly ? 1 : 0, preserveStats ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorIsWritable(MemorySegment it) {
        try { return (int) MH_si_is_writable.invokeExact(it) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorNext(MemorySegment it) {
        try { return (int) MH_si_next.invokeExact(it) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorPrev(MemorySegment it) {
        try { return (int) MH_si_prev.invokeExact(it) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorIsLast(MemorySegment it) {
        try { return (int) MH_si_is_last.invokeExact(it) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static long simpleIteratorGetBlockOffset(MemorySegment it) {
        try { return (long) MH_si_get_block_offset.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int simpleIteratorGetBlockType(MemorySegment it) {
        try { return (int) MH_si_get_block_type.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int simpleIteratorGetBlockLength(MemorySegment it) {
        try { return (int) MH_si_get_block_length.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static MemorySegment simpleIteratorGetBlock(MemorySegment it) {
        try { return (MemorySegment) MH_si_get_block.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorSetBlock(MemorySegment it, MemorySegment block, boolean usePadding) {
        try { return (int) MH_si_set_block.invokeExact(it, block, usePadding ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorInsertBlockAfter(MemorySegment it, MemorySegment block, boolean usePadding) {
        try { return (int) MH_si_insert_after.invokeExact(it, block, usePadding ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean simpleIteratorDeleteBlock(MemorySegment it, boolean usePadding) {
        try { return (int) MH_si_delete_block.invokeExact(it, usePadding ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- Chain --
    public static MemorySegment chainNew() {
        try { return (MemorySegment) MH_chain_new.invokeExact(); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static void chainDelete(MemorySegment c) {
        try { MH_chain_delete.invokeExact(c); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int chainStatus(MemorySegment c) {
        try { return (int) MH_chain_status.invokeExact(c); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainRead(MemorySegment c, MemorySegment filename) {
        try { return (int) MH_chain_read.invokeExact(c, filename) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainReadOgg(MemorySegment c, MemorySegment filename) {
        try { return (int) MH_chain_read_ogg.invokeExact(c, filename) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainWrite(MemorySegment c, boolean usePadding, boolean preserveStats) {
        try { return (int) MH_chain_write.invokeExact(c, usePadding ? 1 : 0, preserveStats ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static void chainMergePadding(MemorySegment c) {
        try { MH_chain_merge_padding.invokeExact(c); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static void chainSortPadding(MemorySegment c) {
        try { MH_chain_sort_padding.invokeExact(c); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainCheckIfTempfileNeeded(MemorySegment c, boolean usePadding) {
        try { return (int) MH_chain_check_if_tempfile_needed.invokeExact(c, usePadding ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- Chain Iterator --
    public static MemorySegment chainIteratorNew() {
        try { return (MemorySegment) MH_iter_new.invokeExact(); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static void chainIteratorDelete(MemorySegment it) {
        try { MH_iter_delete.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static void chainIteratorInit(MemorySegment it, MemorySegment chain) {
        try { MH_iter_init.invokeExact(it, chain); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainIteratorNext(MemorySegment it) {
        try { return (int) MH_iter_next.invokeExact(it) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainIteratorPrev(MemorySegment it) {
        try { return (int) MH_iter_prev.invokeExact(it) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int chainIteratorGetBlockType(MemorySegment it) {
        try { return (int) MH_iter_get_block_type.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static MemorySegment chainIteratorGetBlock(MemorySegment it) {
        try { return (MemorySegment) MH_iter_get_block.invokeExact(it); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainIteratorSetBlock(MemorySegment it, MemorySegment block) {
        try { return (int) MH_iter_set_block.invokeExact(it, block) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainIteratorDeleteBlock(MemorySegment it, boolean usePadding) {
        try { return (int) MH_iter_delete_block.invokeExact(it, usePadding ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainIteratorInsertBefore(MemorySegment it, MemorySegment block) {
        try { return (int) MH_iter_insert_before.invokeExact(it, block) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean chainIteratorInsertAfter(MemorySegment it, MemorySegment block) {
        try { return (int) MH_iter_insert_after.invokeExact(it, block) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
}
