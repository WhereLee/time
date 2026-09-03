package com.reason.common.utils;

import java.util.LinkedList;

/**
 * 队列操作
 * @param <E>
 */
public class LinkedListUtils<E> extends LinkedList<E> {
    /*入链接队列，加到队尾*/
    public void PushLast(E item)
    {
        this.addLast(item);
    }

    /*入链接队列，加到队首*/
    public void PushFirst(E item)
    {
        this.addFirst(item);
    }

    /*出队列，并移除*/
    public E QueryAndRemove()
    {
        if (this.size() > 0){
            E key = this.getFirst();
            this.removeFirst();
            return key;
        }

        return null;
    }

    /*清空队列*/
    public void Clean()
    {
        this.clear();
    }
}
