package org.phoebus.channelfinder.utility;

import org.phoebus.channelfinder.ChannelFinderClient;
import org.phoebus.channelfinder.Tag;
import org.phoebus.framework.jobs.Job;
import org.phoebus.framework.jobs.JobManager;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * A job to remove tag from a list of channels
 *
 * @author Kunal Shroff
 */
public class RemoveTagChannelsJob extends JobRunnableWithCancel {

    private final ChannelFinderClient client;
    private final Tag tag;
    private final Collection<String> channelNames;
    private final BiConsumer<String, Exception> errorHandler;

    /**
     * Submit a job to remove the tag _tag_ to a group of channels
     *
     * @param client - channelfinder client to use
     * @param channelNames - collection of channels to which the tag is to be removed
     * @param tag - the tag to be removed from the channels
     * @param errorHandler - error handler
     * @return Job
     */
    public static Job submit(ChannelFinderClient client,
                                final Collection<String> channelNames,
                                final Tag tag,
                                final BiConsumer<String, Exception> errorHandler)
    {
        return JobManager.schedule("Removing tag : " + tag.getName() + " to " + channelNames.size() + " channels",
                new RemoveTagChannelsJob(client, channelNames, tag, errorHandler));
    }

    private RemoveTagChannelsJob(ChannelFinderClient client, Collection<String> channels, Tag tag, BiConsumer<String, Exception> errorHandler)
    {
        super();
        this.client = client;
        this.errorHandler = errorHandler;
        this.channelNames = channels;
        this.tag = tag;
    }

    @Override
    public String getName()
    {
        return "Removing tag : " + tag.getName() + " to " + channelNames.size() + " channels";
    }

    @Override
    public Runnable getRunnable()
    {
        return () -> {
            client.delete(Tag.Builder.tag(tag), channelNames);
        };
    }

    @Override
    public BiConsumer<String, Exception> getErrorHandler()
    {
        return errorHandler;
    }
}
