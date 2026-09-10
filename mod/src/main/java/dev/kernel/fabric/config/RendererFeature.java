package dev.kernel.fabric.config;

public enum RendererFeature {
    VERTEX("vertex", true),
    POSE("pose", true),
    MODEL("model", true),
    BLOCK_FACE("block_face", true),
    BLOCK_MODEL("block_model", false),
    FLUID("fluid", false),
    CHUNK_UPLOAD("chunk_upload", false),
    CHUNK_QUEUE("chunk_queue", true),
    VISIBILITY("visibility", true),
    QUAD_SORTING("quad_sorting", true);

    private final String key;
    private final boolean modern;

    RendererFeature(String key, boolean modern) { this.key = key; this.modern = modern; }
    public String key() { return key; }
    public String translationKey() { return "kernel.option." + key; }
    public boolean supports(String gameVersion) { return modern || !gameVersion.startsWith("26."); }

    public static RendererFeature forMixin(String className) {
        return switch (className.substring(className.lastIndexOf('.') + 1)) {
            case "VertexConsumerMixin" -> VERTEX;
            case "PoseStackMixin", "PoseStackPoseMixin", "PoseStackPoseAccessor" -> POSE;
            case "ModelPartMixin", "ModelPartCubeMixin" -> MODEL;
            case "BlockMixin" -> BLOCK_FACE;
            case "ModelBlockRendererMixin" -> BLOCK_MODEL;
            case "FluidHeightMixin" -> FLUID;
            case "SectionRenderDispatcherMixin" -> CHUNK_UPLOAD;
            case "ChunkTaskMixin", "ChunkTaskQueueMixin", "ChunkRebuildTaskMixin", "ChunkResortTaskMixin" -> CHUNK_QUEUE;
            case "VisGraphMixin" -> VISIBILITY;
            case "VertexSortingMixin" -> QUAD_SORTING;
            default -> null;
        };
    }
}
