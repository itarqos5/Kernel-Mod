package dev.kernel.fabric.config;

public enum RendererFeature {
    VERTEX("vertex", true),
    POSE("pose", true),
    MODEL("model", true),
    BLOCK_FACE("block_face", true),
    BLOCK_MODEL("block_model", false),
    FLUID("fluid", false),
    CHUNK_UPLOAD("chunk_upload", false),
    SECTION_BUFFERS("section_buffers", false),
    CHUNK_QUEUE("chunk_queue", true),
    CHUNK_UNIFORMS("chunk_uniforms", true),
    VISIBILITY("visibility", true),
    FRUSTUM("frustum", true),
    QUAD_SORTING("quad_sorting", true);

    private final String key;
    private final boolean modern;

    RendererFeature(String key, boolean modern) { this.key = key; this.modern = modern; }
    public String key() { return key; }
    public String translationKey() { return "kernel.option." + key; }
    public boolean supports(String gameVersion) {
        if (this == SECTION_BUFFERS) return gameVersion.equals("1.21.4");
        if (this == CHUNK_UNIFORMS) return gameVersion.equals("1.21.11") || gameVersion.startsWith("26.");
        return modern || !gameVersion.startsWith("26.");
    }

    public static RendererFeature forMixin(String className) {
        return switch (className.substring(className.lastIndexOf('.') + 1)) {
            case "VertexConsumerMixin" -> VERTEX;
            case "PoseStackMixin", "PoseStackPoseMixin", "PoseStackPoseAccessor" -> POSE;
            case "ModelPartMixin", "ModelPartCubeMixin" -> MODEL;
            case "BlockMixin" -> BLOCK_FACE;
            case "ModelBlockRendererMixin", "WeightedModelMixin", "WeightedListMixin" -> BLOCK_MODEL;
            case "FluidHeightMixin" -> FLUID;
            case "SectionRenderDispatcherMixin" -> CHUNK_UPLOAD;
            case "SectionBufferFactoryMixin", "DeferredVertexBufferMixin" -> SECTION_BUFFERS;
            case "ChunkTaskMixin", "ChunkTaskQueueMixin", "ChunkRebuildTaskMixin", "ChunkResortTaskMixin" -> CHUNK_QUEUE;
            case "ChunkUniformMixin" -> CHUNK_UNIFORMS;
            case "VisGraphMixin" -> VISIBILITY;
            case "FrustumMixin" -> FRUSTUM;
            case "VertexSortingMixin" -> QUAD_SORTING;
            default -> null;
        };
    }
}
